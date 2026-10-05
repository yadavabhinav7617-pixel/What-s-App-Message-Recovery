import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import rateLimit from 'express-rate-limit';
import mongoose from 'mongoose';
import { z } from 'zod';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';

declare global {
  namespace Express {
    interface Request {
      user?: {
        sub?: string;
        role?: string;
        email?: string;
      };
    }
  }
}

const app = express();
const PORT = Number(process.env.PORT || 4000);
const mongoUri = process.env.MONGODB_URI || '';
const jwtSecret = process.env.JWT_SECRET || 'dev-secret-change-me';
const adminClients = new Set<any>();

function notifyAdminClients(event: Record<string, unknown>) {
  const payload = `data: ${JSON.stringify(event)}\n\n`;
  for (const client of adminClients) {
    try {
      client.write(payload);
    } catch {
      adminClients.delete(client);
    }
  }
}

const isProduction = process.env.NODE_ENV === 'production';

app.disable('x-powered-by');
app.use(helmet({
  crossOriginResourcePolicy: { policy: 'cross-origin' },
  contentSecurityPolicy: false
}));
app.use(cors({
  origin: (origin, callback) => {
    const raw = process.env.CORS_ORIGIN || '*';
    const allowed = raw.split(',').map((s) => s.trim()).filter(Boolean);
    if (!origin || allowed.includes('*') || allowed.includes(origin)) {
      callback(null, true);
      return;
    }
    callback(new Error('CORS not allowed')); 
  },
  credentials: true
}));
app.use(express.json({ limit: '2mb' }));
app.use(morgan(isProduction ? 'combined' : 'dev'));
app.use(rateLimit({ windowMs: 60_000, max: 120, standardHeaders: true, legacyHeaders: false }));

// Health Check Endpoints (Placed BEFORE DB connection middleware so health checks never fail due to DB state)
app.get(['/', '/health', '/api/health'], (_req, res) => {
  res.json({
    ok: true,
    service: 'NotifyVault Backend',
    status: 'online',
    dbConnected: mongoose.connection.readyState === 1,
    timestamp: new Date().toISOString()
  });
});

let isDbConnected = false;

async function bootstrapAdmin() {
  if (!process.env.ADMIN_INITIAL_SETUP_SECRET) return;
  const adminEmail = process.env.SUPER_ADMIN_EMAIL || 'admin@notifyvault.local';
  const adminPassword = process.env.SUPER_ADMIN_PASSWORD || 'ChangeMe123!';
  const exists = await Admin.findOne({ email: adminEmail });
  if (!exists) {
    const passwordHash = await bcrypt.hash(adminPassword, 12);
    await Admin.create({ email: adminEmail, passwordHash, role: 'SUPER_ADMIN' });
  }
}

async function ensureDbConnected() {
  if (isDbConnected && mongoose.connection.readyState === 1) {
    return;
  }
  if (!mongoUri) {
    throw new Error('MONGODB_URI environment variable is missing on Vercel.');
  }
  try {
    await mongoose.connect(mongoUri, { serverSelectionTimeoutMS: 5000 });
    isDbConnected = true;
    await bootstrapAdmin();
  } catch (err) {
    console.error('MongoDB connection error:', err);
    throw err;
  }
}

// Ensure Database is connected for all API routes
app.use('/api', async (_req, _res, next) => {
  try {
    await ensureDbConnected();
    next();
  } catch (err) {
    next(err);
  }
});

const UserSchema = new mongoose.Schema({
  email: { type: String, required: true, unique: true },
  passwordHash: { type: String, required: true },
  name: { type: String, required: true },
  devices: [{ type: mongoose.Schema.Types.ObjectId, ref: 'Device' }],
  createdAt: { type: Date, default: Date.now }
}, { collection: 'users' });

const DeviceSchema = new mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  deviceId: { type: String, required: true, unique: true },
  deviceName: { type: String, default: 'This device' },
  model: { type: String, default: '' },
  androidVersion: { type: String, default: '' },
  appVersion: { type: String, default: '' },
  status: { type: String, enum: ['ACTIVE', 'REVOKED'], default: 'ACTIVE' },
  online: { type: Boolean, default: false },
  lastSeen: { type: Date, default: Date.now },
  lastSync: { type: Date, default: null },
  firstRegisteredAt: { type: Date, default: Date.now },
  lastActiveAt: { type: Date, default: Date.now },
  authTokenHash: { type: String, default: '' },
  createdAt: { type: Date, default: Date.now },
  updatedAt: { type: Date, default: Date.now }
}, { collection: 'devices' });

const MessageSchema = new mongoose.Schema({
  messageId: { type: String, required: true, unique: true },
  accountId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  deviceId: { type: String, required: true },
  sender: { type: String, default: '' },
  messageText: { type: String, default: '' },
  capturedAt: { type: Date, required: true },
  sourcePackage: { type: String, default: '' },
  notificationTitle: { type: String, default: '' },
  conversationId: { type: String, default: '' },
  createdAt: { type: Date, default: Date.now },
  updatedAt: { type: Date, default: Date.now }
}, { collection: 'messages' });

MessageSchema.index({ accountId: 1, capturedAt: -1 });
MessageSchema.index({ deviceId: 1, capturedAt: -1 });
MessageSchema.index({ sender: 1 });

const AuditLogSchema = new mongoose.Schema({
  actor: { type: String, default: 'system' },
  action: { type: String, required: true },
  details: { type: String, default: '' },
  createdAt: { type: Date, default: Date.now }
}, { collection: 'auditLogs' });

const AdminSchema = new mongoose.Schema({
  email: { type: String, required: true, unique: true },
  passwordHash: { type: String, required: true },
  role: { type: String, default: 'SUPER_ADMIN' },
  createdAt: { type: Date, default: Date.now }
}, { collection: 'admins' });

const User = mongoose.model('User', UserSchema);
const Device = mongoose.model('Device', DeviceSchema);
const Message = mongoose.model('Message', MessageSchema);
const AuditLog = mongoose.model('AuditLog', AuditLogSchema);
const Admin = mongoose.model('Admin', AdminSchema);

function signJwt(payload: Record<string, unknown>) {
  return jwt.sign(payload, jwtSecret, { expiresIn: '7d' });
}

function requireAuth(req: any, res: any, next: any) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) return res.status(401).json({ message: 'Authentication required' });
  try {
    const decoded = jwt.verify(token, jwtSecret) as any;
    req.user = decoded;
    next();
  } catch {
    return res.status(401).json({ message: 'Invalid or expired token' });
  }
}

async function requireAdmin(req: any, res: any, next: any) {
  if (!req.user || !req.user.role || req.user.role !== 'SUPER_ADMIN') {
    return res.status(403).json({ message: 'Admin access required' });
  }
  const admin = await Admin.findById(req.user.sub);
  if (!admin) return res.status(403).json({ message: 'Admin not found' });
  next();
}

const registerSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
  name: z.string().min(2)
});

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8)
});

const deviceRegisterSchema = z.object({
  deviceId: z.string().min(8),
  deviceName: z.string().min(2),
  model: z.string().optional(),
  androidVersion: z.string().optional(),
  appVersion: z.string().optional(),
  accountEmail: z.string().email()
});

const adminRateLimiter = rateLimit({
  windowMs: 15 * 60_000,
  max: 10,
  standardHeaders: true,
  legacyHeaders: false,
  message: { message: 'Too many admin login attempts, try again later.' }
});

app.get('/api/admin/stream', requireAuth, requireAdmin, (req, res) => {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache, no-transform');
  res.setHeader('Connection', 'keep-alive');
  res.flushHeaders?.();

  adminClients.add(res);
  res.write(`data: ${JSON.stringify({ type: 'connected', timestamp: Date.now() })}\n\n`);

  req.on('close', () => {
    adminClients.delete(res);
  });
});

app.post('/api/auth/register', async (req, res) => {
  const parsed = registerSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ message: 'Invalid registration data' });

  const { email, password, name } = parsed.data;
  const exists = await User.findOne({ email });
  if (exists) return res.status(409).json({ message: 'User already exists' });

  const passwordHash = await bcrypt.hash(password, 12);
  const user = await User.create({ email, passwordHash, name });
  res.status(201).json({ userId: user._id, email: user.email });
});

app.post('/api/auth/login', async (req, res) => {
  const parsed = loginSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ message: 'Invalid login payload' });

  const { email, password } = parsed.data;
  const user = await User.findOne({ email });
  if (!user) return res.status(401).json({ message: 'Invalid credentials' });

  const valid = await bcrypt.compare(password, user.passwordHash);
  if (!valid) return res.status(401).json({ message: 'Invalid credentials' });

  const token = signJwt({ sub: user._id.toString(), role: 'USER', email: user.email });
  res.json({ token, userId: user._id });
});

app.post('/api/devices/register', requireAuth, async (req, res) => {
  const parsed = deviceRegisterSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ message: 'Invalid device registration payload' });

  const currentUser = req.user;
  const user = await User.findOne({ email: parsed.data.accountEmail });
  if (!currentUser || !user || user._id.toString() !== (currentUser.sub || '')) {
    return res.status(403).json({ message: 'Device registration forbidden for this account' });
  }

  const device = await Device.findOneAndUpdate(
    { deviceId: parsed.data.deviceId },
    {
      userId: user._id,
      deviceId: parsed.data.deviceId,
      deviceName: parsed.data.deviceName,
      model: parsed.data.model || '',
      androidVersion: parsed.data.androidVersion || '',
      appVersion: parsed.data.appVersion || '',
      status: 'ACTIVE',
      online: true,
      lastSeen: new Date(),
      lastActiveAt: new Date(),
      updatedAt: new Date()
    },
    { upsert: true, new: true }
  );

  await AuditLog.create({ actor: parsed.data.deviceId, action: 'device_registration', details: `User=${user.email}` });
  res.status(201).json({ deviceId: device.deviceId, status: 'registered' });
});

app.post('/api/messages/sync', requireAuth, async (req, res) => {
  const currentUser = req.user;
  if (!currentUser || !currentUser.sub) return res.status(401).json({ message: 'Authentication required' });

  const schema = z.object({
    messageId: z.string().min(1),
    sender: z.string().optional(),
    messageText: z.string().optional(),
    capturedAt: z.number().optional(),
    deviceId: z.string().min(1),
    sourcePackage: z.string().optional(),
    notificationTitle: z.string().optional(),
    conversationId: z.string().optional(),
    eventId: z.string().optional()
  });

  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ message: 'Invalid message payload' });

  const { messageId, sender, messageText, capturedAt, deviceId, sourcePackage, notificationTitle, conversationId } = parsed.data;
  const device = await Device.findOne({ deviceId, userId: currentUser.sub, status: 'ACTIVE' });
  if (!device) return res.status(403).json({ message: 'Unauthorized device' });

  const exists = await Message.findOne({ messageId });
  if (exists) return res.status(200).json({ accepted: true, duplicate: true });

  await Message.create({
    messageId,
    accountId: currentUser.sub,
    deviceId,
    sender: sender || '',
    messageText: messageText || '',
    capturedAt: new Date(capturedAt || Date.now()),
    sourcePackage: sourcePackage || '',
    notificationTitle: notificationTitle || '',
    conversationId: conversationId || '',
    createdAt: new Date(),
    updatedAt: new Date()
  });

  device.lastSync = new Date();
  device.lastSeen = new Date();
  device.online = true;
  device.updatedAt = new Date();
  await device.save();

  notifyAdminClients({ type: 'new_message', deviceId, messageId, timestamp: Date.now() });

  res.status(200).json({ accepted: true });
});

app.get('/api/messages', requireAuth, async (req, res) => {
  const currentUser = req.user;
  if (!currentUser || !currentUser.sub) return res.status(401).json({ message: 'Authentication required' });
  const items = await Message.find({ accountId: currentUser.sub }).sort({ capturedAt: -1 }).limit(50);
  res.json({ messages: items });
});

app.get('/api/devices', requireAuth, async (req, res) => {
  const currentUser = req.user;
  if (!currentUser || !currentUser.sub) return res.status(401).json({ message: 'Authentication required' });
  const devices = await Device.find({ userId: currentUser.sub }).sort({ lastSeen: -1 });
  res.json({ devices });
});

app.post('/api/admin/login', adminRateLimiter, async (req, res) => {
  const parsed = loginSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ message: 'Invalid admin credentials' });

  const admin = await Admin.findOne({ email: parsed.data.email });
  if (!admin) return res.status(401).json({ message: 'Unauthorized' });
  const valid = await bcrypt.compare(parsed.data.password, admin.passwordHash);
  if (!valid) return res.status(401).json({ message: 'Unauthorized' });

  const token = signJwt({ sub: admin._id.toString(), role: 'SUPER_ADMIN', email: admin.email });
  await AuditLog.create({ actor: admin.email, action: 'admin_login' });
  res.json({ token });
});

app.get('/api/admin/dashboard', requireAuth, requireAdmin, async (_req, res) => {
  const [totalDevices, onlineDevices, offlineDevices, totalMessages, todayMessages] = await Promise.all([
    Device.countDocuments({}),
    Device.countDocuments({ online: true }),
    Device.countDocuments({ online: false }),
    Message.countDocuments({}),
    Message.countDocuments({ capturedAt: { $gte: new Date(new Date().setHours(0, 0, 0, 0)) } })
  ]);
  res.json({ totalRegisteredDevices: totalDevices, onlineDevices, offlineDevices, totalSynchronizedMessages: totalMessages, todaysMessages: todayMessages });
});

app.get('/api/admin/devices', requireAuth, requireAdmin, async (_req, res) => {
  const devices = await Device.find({}).sort({ lastSeen: -1 });
  res.json({ devices });
});

app.get('/api/admin/messages', requireAuth, requireAdmin, async (req, res) => {
  const limit = Math.min(Number(req.query.limit || 50), 200);
  const messages = await Message.find({}).sort({ capturedAt: -1 }).limit(limit);
  res.json({ messages });
});

app.post('/api/admin/revoke-device', requireAuth, requireAdmin, async (req, res) => {
  const { deviceId } = req.body || {};
  if (!deviceId) return res.status(400).json({ message: 'deviceId is required' });
  const device = await Device.findOne({ deviceId });
  if (!device) return res.status(404).json({ message: 'Device not found' });
  device.status = 'REVOKED';
  device.online = false;
  device.updatedAt = new Date();
  await device.save();
  await AuditLog.create({ actor: 'admin', action: 'device_revoke', details: `deviceId=${deviceId}` });
  res.json({ ok: true, deviceId: device.deviceId });
});

app.use((err: any, _req: any, res: any, _next: any) => {
  console.error('Server error:', err);
  res.status(500).json({
    message: err?.message || 'Internal server error'
  });
});

if (!process.env.VERCEL) {
  ensureDbConnected().then(() => {
    app.listen(PORT, () => {
      console.log(`NotifyVault backend running on http://localhost:${PORT}`);
    });
  }).catch((err) => {
    console.error('Failed to start server locally:', err);
  });
}

export default app;
