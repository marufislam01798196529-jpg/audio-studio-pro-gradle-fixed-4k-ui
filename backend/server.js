const express = require('express');
const multer = require('multer');
const AdmZip = require('adm-zip');
const { spawn } = require('child_process');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const app = express();
app.use(express.json({ limit: '2mb' }));

const PORT = process.env.PORT || 8080;
const AUTH_TOKEN = process.env.XTHREX_BACKEND_TOKEN || '';
const WORK_ROOT = path.resolve(process.env.XTHREX_WORK_ROOT || '/tmp/xthrex-containers');
fs.mkdirSync(WORK_ROOT, { recursive: true });

const upload = multer({
  dest: path.join(WORK_ROOT, '_uploads'),
  limits: { fileSize: 50 * 1024 * 1024 }
});

const buildJobs = new Map();
const taskQueue = new Map();

// Bearer Token Auth Middleware (if XTHREX_BACKEND_TOKEN is configured)
function requireAuth(req, res, next) {
  if (!AUTH_TOKEN) return next();
  const header = req.headers.authorization || '';
  if (header !== `Bearer ${AUTH_TOKEN}`) {
    return res.status(401).json({ error: 'Unauthorized backend token' });
  }
  next();
}

// Strict command allowlist for isolated terminal execution
const ALLOWED_CMD_PREFIXES = [
  'ls', 'pwd', 'cat', 'find', 'grep', 'echo',
  './gradlew', 'gradle', 'npm', 'npx', 'node', 'python', 'python3', 'pytest', 'git', 'flutter'
];

function isCommandAllowed(cmd) {
  const trimmed = (cmd || '').trim();
  if (!trimmed || /[;&|`$<>]/.test(trimmed)) return false;
  const bin = trimmed.split(/\s+/)[0];
  return ALLOWED_CMD_PREFIXES.includes(bin);
}

function safeResolveProjectDir(projectId) {
  const safeId = (projectId || 'default').replace(/[^A-Za-z0-9_-]/g, '_');
  const dir = path.resolve(WORK_ROOT, safeId);
  if (!dir.startsWith(WORK_ROOT)) throw new Error('Invalid project path');
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

app.get('/api/health', requireAuth, (req, res) => {
  res.json({
    status: 'ok',
    isolated: true,
    gradleAvailable: fs.existsSync(process.env.ANDROID_HOME || '/opt/android-sdk'),
    timestamp: new Date().toISOString()
  });
});

app.post('/api/terminal/exec', requireAuth, (req, res) => {
  const { projectId, workingDir, command } = req.body || {};
  if (!isCommandAllowed(command)) {
    return res.status(403).json({
      exitCode: 126,
      stdout: '',
      stderr: 'Command blocked by X-THREX container isolation policy.'
    });
  }

  const projectRoot = safeResolveProjectDir(projectId);
  const cwd = path.resolve(projectRoot, (workingDir || '.').replace(/\.\./g, ''));
  if (!cwd.startsWith(projectRoot)) {
    return res.status(403).json({ exitCode: 126, stdout: '', stderr: 'Path traversal blocked.' });
  }

  const parts = command.trim().split(/\s+/);
  const child = spawn(parts[0], parts.slice(1), {
    cwd,
    timeout: 60000,
    env: {
      PATH: process.env.PATH,
      HOME: projectRoot,
      ANDROID_HOME: process.env.ANDROID_HOME || '/opt/android-sdk'
    }
  });

  let stdout = '';
  let stderr = '';
  child.stdout.on('data', d => { if (stdout.length < 32768) stdout += d.toString(); });
  child.stderr.on('data', d => { if (stderr.length < 32768) stderr += d.toString(); });
  child.on('close', code => {
    res.json({ exitCode: code ?? 1, stdout, stderr });
  });
  child.on('error', err => {
    res.json({ exitCode: 1, stdout, stderr: err.message });
  });
});

app.post('/api/builds', requireAuth, upload.single('projectZip'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'projectZip file required' });
  const jobId = crypto.randomUUID();
  const projectId = req.body.projectId || jobId;
  const buildType = req.body.buildType || 'DEBUG_APK';
  const workDir = path.join(WORK_ROOT, `build_${jobId}`);
  fs.mkdirSync(workDir, { recursive: true });

  const job = {
    jobId,
    projectId,
    buildType,
    status: 'QUEUED',
    logs: `Job ${jobId} queued for ${buildType}...\n`,
    errorCount: 0,
    artifactPath: null,
    artifactUrl: null,
    createdAt: Date.now()
  };
  buildJobs.set(jobId, job);

  // Extract ZIP safely with Zip-Slip check and run Gradle asynchronously
  setImmediate(() => runIsolatedBuild(job, req.file.path, workDir));

  res.status(202).json({
    jobId: job.jobId,
    status: job.status,
    logs: job.logs,
    errorCount: 0,
    artifactUrl: null
  });
});

function runIsolatedBuild(job, zipPath, workDir) {
  try {
    job.status = 'RUNNING';
    const zip = new AdmZip(zipPath);
    for (const entry of zip.getEntries()) {
      const dest = path.resolve(workDir, entry.entryName);
      if (!dest.startsWith(workDir)) continue;
      if (entry.isDirectory) {
        fs.mkdirSync(dest, { recursive: true });
      } else {
        fs.mkdirSync(path.dirname(dest), { recursive: true });
        fs.writeFileSync(dest, entry.getData());
      }
    }
    fs.unlinkSync(zipPath);

    // Find build root (either workDir or unwrapped subfolder)
    let buildRoot = workDir;
    const entries = fs.readdirSync(workDir);
    if (entries.length === 1 && fs.statSync(path.join(workDir, entries[0])).isDirectory()) {
      buildRoot = path.join(workDir, entries[0]);
    }

    const gradleTask = job.buildType === 'AAB'
      ? 'bundleRelease'
      : job.buildType === 'RELEASE_APK'
        ? 'assembleRelease'
        : 'assembleDebug';

    const gradleCmd = fs.existsSync(path.join(buildRoot, 'gradlew')) ? './gradlew' : 'gradle';
    if (gradleCmd === './gradlew') fs.chmodSync(path.join(buildRoot, 'gradlew'), 0o755);

    const child = spawn(gradleCmd, [gradleTask, '--no-daemon'], {
      cwd: buildRoot,
      timeout: 300000,
      env: { ...process.env, ANDROID_HOME: process.env.ANDROID_HOME || '/opt/android-sdk' }
    });

    child.stdout.on('data', d => { job.logs += d.toString(); });
    child.stderr.on('data', d => { job.logs += d.toString(); });
    child.on('close', code => {
      if (code === 0) {
        const artifact = findArtifact(buildRoot, job.buildType);
        job.status = 'SUCCESS';
        if (artifact) {
          job.artifactPath = artifact;
          job.artifactUrl = `/api/builds/${job.jobId}/artifact`;
        }
      } else {
        job.status = 'FAILED';
        job.errorCount = (job.logs.match(/error:/gi) || []).length || 1;
      }
    });
    child.on('error', err => {
      job.status = 'FAILED';
      job.errorCount = 1;
      job.logs += `\nBuild runner error: ${err.message}\n`;
    });
  } catch (err) {
    job.status = 'FAILED';
    job.errorCount = 1;
    job.logs += `\nExtraction failure: ${err.message}\n`;
  }
}

function findArtifact(rootDir, buildType) {
  const ext = buildType === 'AAB' ? '.aab' : '.apk';
  const stack = [rootDir];
  while (stack.length > 0) {
    const dir = stack.pop();
    for (const name of fs.readdirSync(dir)) {
      const full = path.join(dir, name);
      if (fs.statSync(full).isDirectory()) stack.push(full);
      else if (name.endsWith(ext)) return full;
    }
  }
  return null;
}

app.get('/api/builds/:jobId', requireAuth, (req, res) => {
  const job = buildJobs.get(req.params.jobId);
  if (!job) return res.status(404).json({ error: 'Build job not found' });
  res.json({
    jobId: job.jobId,
    status: job.status,
    logs: job.logs.slice(-16000),
    errorCount: job.errorCount,
    artifactUrl: job.artifactUrl
  });
});

app.get('/api/builds/:jobId/artifact', requireAuth, (req, res) => {
  const job = buildJobs.get(req.params.jobId);
  if (!job || !job.artifactPath || !fs.existsSync(job.artifactPath)) {
    return res.status(404).json({ error: 'Artifact not available' });
  }
  res.download(job.artifactPath);
});

app.post('/api/tasks/enqueue', requireAuth, (req, res) => {
  const taskId = crypto.randomUUID();
  taskQueue.set(taskId, { taskId, payload: req.body, status: 'QUEUED', createdAt: Date.now() });
  res.status(202).json({ taskId, status: 'QUEUED' });
});

app.get('/api/tasks/:taskId', requireAuth, (req, res) => {
  const t = taskQueue.get(req.params.taskId);
  if (!t) return res.status(404).json({ error: 'Task not found' });
  res.json(t);
});

// Cleanup artifacts older than 2 hours
setInterval(() => {
  const cutoff = Date.now() - 2 * 60 * 60 * 1000;
  for (const [id, job] of buildJobs.entries()) {
    if (job.createdAt < cutoff) {
      fs.rmSync(path.join(WORK_ROOT, `build_${id}`), { recursive: true, force: true });
      buildJobs.delete(id);
    }
  }
}, 15 * 60 * 1000);

app.listen(PORT, () => {
  console.log(`X-THREX isolated cloud backend listening on port ${PORT}`);
});
