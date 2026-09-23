'use client';
import { useState } from 'react';
import { Upload, ArrowRight } from 'lucide-react';
import { Button } from './ui/button';
import { Dialog } from './ui/dialog';
import { messages, languageNames } from '@/lib/i18n';
import { fromSwissLocal, swissLocal } from '@/lib/dates';
import {
  emptyJob,
  jobData,
  statuses,
  type Job,
  type JobData,
  type Profile,
  type Language,
  type Document,
  type Account,
  type Config,
} from '@/lib/types';
import { api, refreshCsrf } from '@/lib/api';
export function JobForm({
  job,
  profiles,
  lang,
  onSave,
  onClose,
}: {
  job: Job | null;
  profiles: Profile[];
  lang: Language;
  onSave: (data: JobData) => Promise<void>;
  onClose: () => void;
}) {
  const t = messages[lang];
  const [data, setData] = useState<JobData>(job ? jobData(job) : emptyJob());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [interview, setInterview] = useState(swissLocal(data.interviewDate));
  const field = (
    key: keyof JobData,
    label: string,
    type = 'text',
    extra: Record<string, unknown> = {},
  ) => (
    <label className="field">
      {label}
      <input
        type={type}
        value={String(data[key] ?? '')}
        onChange={(e) =>
          setData({
            ...data,
            [key]:
              type === 'number'
                ? e.target.value === ''
                  ? null
                  : Number(e.target.value)
                : type === 'date'
                  ? e.target.value || null
                  : e.target.value,
          })
        }
        {...extra}
      />
    </label>
  );
  const statusKeys = {
    INTERESTED: 'interested',
    APPLIED: 'applied',
    INTERVIEW: 'interview',
    OFFER: 'offer',
    REJECTED: 'rejected',
  } as const;
  const statusLabel = (s: JobData['status']) => t[statusKeys[s]];
  return (
    <Dialog
      open
      onOpenChange={(v) => !v && onClose()}
      title={job ? t.edit : t.add}
      description={t.addNotes}
      wide
    >
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          setError('');
          try {
            await onSave({ ...data, interviewDate: fromSwissLocal(interview) });
            onClose();
          } catch (e) {
            setError((e as Error).message);
          } finally {
            setBusy(false);
          }
        }}
      >
        <div className="form-grid">
          {field('title', t.role, 'text', { required: true, maxLength: 150, autoFocus: true })}
          {field('company', t.companyName, 'text', { required: true, maxLength: 150 })}
          <label className="field">
            {t.status}
            <select
              value={data.status}
              onChange={(e) => setData({ ...data, status: e.target.value as JobData['status'] })}
            >
              {statuses.map((s) => (
                <option key={s} value={s}>
                  {statusLabel(s)}
                </option>
              ))}
            </select>
          </label>
          {field('location', t.location, 'text', { maxLength: 100 })}
          <label className="field">
            {t.canton}
            <select
              value={data.canton}
              onChange={(e) => setData({ ...data, canton: e.target.value })}
            >
              <option value="">—</option>
              {[
                'AG',
                'AI',
                'AR',
                'BE',
                'BL',
                'BS',
                'FR',
                'GE',
                'GL',
                'GR',
                'JU',
                'LU',
                'NE',
                'NW',
                'OW',
                'SG',
                'SH',
                'SO',
                'SZ',
                'TG',
                'TI',
                'UR',
                'VD',
                'VS',
                'ZG',
                'ZH',
              ].map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </label>
          {field('workload', t.workload, 'number', { min: 1, max: 100 })}
          {field('url', t.url, 'url', { maxLength: 2000 })}
          <label className="field">
            {t.source}
            <select
              value={data.source}
              onChange={(e) => setData({ ...data, source: e.target.value })}
            >
              {[
                'Company website',
                'LinkedIn',
                'jobs.ch',
                'Indeed',
                'Jobup',
                'Referral',
                'Other',
              ].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </label>
          {field('salaryMin', t.salaryMin, 'number', { min: 0, max: 10000000 })}
          {field('salaryMax', t.salaryMax, 'number', { min: data.salaryMin ?? 0, max: 10000000 })}
          <label className="field">
            {t.profile}
            <select
              value={data.profileId ?? ''}
              onChange={(e) => setData({ ...data, profileId: e.target.value || null })}
            >
              <option value="">{t.chooseProfile}</option>
              {profiles.map((p) => (
                <option value={p.id} key={p.id}>
                  {p.name} · {p.language.toUpperCase()}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            {t.language}
            <select
              value={data.language}
              onChange={(e) => setData({ ...data, language: e.target.value as Language })}
            >
              {Object.entries(languageNames).map(([id, name]) => (
                <option value={id} key={id}>
                  {name}
                </option>
              ))}
            </select>
          </label>
          <label className="field span-2">
            {t.description}
            <textarea
              rows={5}
              value={data.description}
              maxLength={40000}
              placeholder={t.descriptionHint}
              onChange={(e) => setData({ ...data, description: e.target.value })}
            />
          </label>
          {field('contactName', t.contactName, 'text', { maxLength: 100 })}
          {field('contactEmail', t.contactEmail, 'email', { maxLength: 254 })}
          {field('appliedDate', t.appliedDate, 'date')}
          <label className="field">
            {t.interviewDate}
            <input
              type="datetime-local"
              value={interview}
              onChange={(e) => setInterview(e.target.value)}
            />
            <small>{t.timezone}</small>
          </label>
          {field('followUpDate', t.followUpDate, 'date')}
          <label className="field span-2">
            {t.notes}
            <textarea
              rows={3}
              maxLength={10000}
              value={data.notes}
              onChange={(e) => setData({ ...data, notes: e.target.value })}
            />
          </label>
        </div>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <div className="form-footer">
          <Button type="button" variant="outline" onClick={onClose}>
            {t.cancel}
          </Button>
          <Button disabled={busy}>
            {busy ? t.busy : job ? t.save : t.add}
            <ArrowRight size={16} />
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
export function ProfileForm({
  profile,
  lang,
  demo,
  onSave,
  onClose,
  onUploaded,
}: {
  profile: Profile | null;
  lang: Language;
  demo: boolean;
  onSave: (data: Omit<Profile, 'id' | 'version'>) => Promise<void>;
  onClose: () => void;
  onUploaded: (doc: Document) => void;
}) {
  const t = messages[lang];
  const [data, setData] = useState({
    name: profile?.name ?? '',
    language: profile?.language ?? lang,
    text: profile?.text ?? '',
    documentId: profile?.documentId ?? null,
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function upload(file: File) {
    setBusy(true);
    setError('');
    try {
      if (demo) throw new Error(t.aiDemo);
      const form = new FormData();
      form.set('file', file);
      const doc = await api<Document>('/documents', 'POST', form);
      onUploaded(doc);
      setData({ ...data, text: doc.text, documentId: doc.id });
      if (!doc.text.trim())
        setError('No text was found. Paste the CV text below; scanned PDFs require OCR.');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog
      open
      onOpenChange={(v) => !v && onClose()}
      title={profile ? t.profiles : t.addProfile}
      description={t.cvHint}
    >
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          setError('');
          try {
            await onSave(data);
            onClose();
          } catch (e) {
            setError((e as Error).message);
          } finally {
            setBusy(false);
          }
        }}
      >
        <div className="form-grid">
          {' '}
          <label className="field">
            {t.profileName}
            <input
              required
              maxLength={100}
              value={data.name}
              autoFocus
              onChange={(e) => setData({ ...data, name: e.target.value })}
            />
          </label>
          <label className="field">
            {t.language}
            <select
              value={data.language}
              onChange={(e) => setData({ ...data, language: e.target.value as Language })}
            >
              {Object.entries(languageNames).map(([id, name]) => (
                <option key={id} value={id}>
                  {name}
                </option>
              ))}
            </select>
          </label>
        </div>
        <label className={`upload-area ${busy ? 'disabled' : ''}`}>
          <Upload size={22} />
          <strong>{t.upload}</strong>
          <span>{t.uploadHint}</span>
          <input
            type="file"
            accept=".pdf,.txt"
            disabled={busy}
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) void upload(file);
            }}
          />
        </label>
        <label className="field">
          {t.cvText}
          <textarea
            rows={12}
            required
            maxLength={40000}
            value={data.text}
            onChange={(e) => setData({ ...data, text: e.target.value })}
          />
        </label>
        {data.documentId && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => setData({ ...data, documentId: null })}
          >
            {t.remove} PDF
          </Button>
        )}
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <div className="form-footer">
          <Button type="button" variant="outline" onClick={onClose}>
            {t.cancel}
          </Button>
          <Button disabled={busy}>{busy ? t.busy : t.save}</Button>
        </div>
      </form>
    </Dialog>
  );
}
export function AuthForm({
  mode,
  lang,
  config,
  onMode,
  onSuccess,
  onClose,
}: {
  mode: 'login' | 'register';
  lang: Language;
  config: Config;
  onMode: (mode: 'login' | 'register') => void;
  onSuccess: (account: Account) => Promise<void>;
  onClose: () => void;
}) {
  const t = messages[lang];
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  return (
    <Dialog
      open
      onOpenChange={(v) => !v && onClose()}
      title={mode === 'login' ? t.welcome : t.welcomeNew}
      description={t.authNote}
    >
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          setError('');
          setBusy(true);
          const form = new FormData(e.currentTarget);
          try {
            const user = await api<Account>(`/auth/${mode}`, 'POST', {
              email: form.get('email'),
              password: form.get('password'),
              name: form.get('name') || null,
            });
            await refreshCsrf();
            await onSuccess(user);
            onClose();
          } catch (e) {
            setError((e as Error).message);
          } finally {
            setBusy(false);
          }
        }}
        className="auth-form"
      >
        {config.google && (
          <>
            <Button variant="outline" type="button" asChild>
              <a href="/oauth2/authorization/google">{t.google}</a>
            </Button>
            <div className="divider">
              <span>{t.or}</span>
            </div>
          </>
        )}
        {mode === 'register' && (
          <label className="field">
            {t.name}
            <input name="name" required maxLength={100} autoComplete="name" />
          </label>
        )}
        <label className="field">
          {t.email}
          <input type="email" name="email" required autoComplete="email" maxLength={254} />
        </label>
        <label className="field">
          {t.password}
          <input
            type="password"
            name="password"
            required
            minLength={12}
            maxLength={72}
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
          />
          <small>{t.passwordHint}</small>
        </label>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <Button disabled={busy}>
          {busy ? t.busy : mode === 'login' ? t.signIn : t.createAccount}
          <ArrowRight size={16} />
        </Button>
        {(config.registration || mode === 'register') && (
          <p className="auth-switch">
            {mode === 'login' ? t.accountQuestion : t.alreadyAccount}{' '}
            <button
              type="button"
              className="text-link"
              onClick={() => {
                setError('');
                onMode(mode === 'login' ? 'register' : 'login');
              }}
            >
              {mode === 'login' ? t.createAccount : t.signIn}
            </button>
          </p>
        )}
      </form>
    </Dialog>
  );
}
