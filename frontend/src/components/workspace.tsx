'use client';
import { useEffect, useRef, useState } from 'react';
import {
  ArrowDownToLine,
  ArrowRight,
  ArrowUpRight,
  Bell,
  BriefcaseBusiness,
  CalendarDays,
  ChartNoAxesCombined,
  Check,
  CheckCheck,
  ChevronRight,
  CircleHelp,
  Clock3,
  FileText,
  FolderOpen,
  Globe2,
  LayoutDashboard,
  LayoutGrid,
  List,
  LoaderCircle,
  LogOut,
  MapPin,
  Menu,
  MoreHorizontal,
  Plus,
  Search,
  Settings2,
  ShieldCheck,
  Sparkles,
  Target,
  Trash2,
  TrendingUp,
  UserRound,
  Users,
  X,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { AuthForm, JobForm, ProfileForm } from '@/components/forms';
import { api, downloadText, refreshCsrf } from '@/lib/api';
import { demoJobs, demoProfiles } from '@/lib/demo';
import { dateLabel, followUp, swissToday, calendarFile } from '@/lib/dates';
import { languageNames, locale, messages } from '@/lib/i18n';
import {
  jobData,
  statuses,
  type Account,
  type Config,
  type Document,
  type Job,
  type Language,
  type Profile,
  type Status,
} from '@/lib/types';

type Page =
  'overview' | 'applications' | 'profiles' | 'documents' | 'calendar' | 'insights' | 'settings';
type DetailTab = 'details' | 'analysis' | 'coverLetter' | 'interviewPrep' | 'tailoring';
const statusKeys = {
  INTERESTED: 'interested',
  APPLIED: 'applied',
  INTERVIEW: 'interview',
  OFFER: 'offer',
  REJECTED: 'rejected',
} as const;
const navItems = [
  { id: 'overview', icon: LayoutDashboard },
  { id: 'applications', icon: BriefcaseBusiness },
  { id: 'profiles', icon: FileText },
  { id: 'documents', icon: FolderOpen },
  { id: 'calendar', icon: CalendarDays },
  { id: 'insights', icon: ChartNoAxesCombined },
] as const;

export default function Workspace() {
  const [lang, setLang] = useState<Language>('en');
  const t = messages[lang];
  const [page, setPage] = useState<Page>('overview');
  const [jobs, setJobs] = useState<Job[]>(demoJobs);
  const [profiles, setProfiles] = useState<Profile[]>(demoProfiles);
  const [documents, setDocuments] = useState<Document[]>([]);
  const [account, setAccount] = useState<Account | null>(null);
  const [config, setConfig] = useState<Config>({
    ai: false,
    google: false,
    billing: false,
    registration: true,
    email: false,
  });
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<Status | 'all' | 'active'>('all');
  const [board, setBoard] = useState(false);
  const [mobileNav, setMobileNav] = useState(false);
  const [auth, setAuth] = useState<'login' | 'register' | null>(null);
  const [jobForm, setJobForm] = useState<Job | null | undefined>();
  const [profileForm, setProfileForm] = useState<Profile | null | undefined>();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [tab, setTab] = useState<DetailTab>('details');
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState('');
  const [confirmation, setConfirmation] = useState<{
    text: string;
    action: () => Promise<void>;
  } | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const uploadEntry = useRef<string | null>(null);
  const demo = !account;
  const selected = jobs.find((j) => j.id === selectedId);
  const counts = Object.fromEntries(
    statuses.map((s) => [s, jobs.filter((j) => j.status === s).length]),
  ) as Record<Status, number>;
  const filtered = jobs.filter(
    (j) =>
      (filter === 'all' ||
        (filter === 'active'
          ? ['APPLIED', 'INTERVIEW'].includes(j.status)
          : j.status === filter)) &&
      `${j.title} ${j.company} ${j.location} ${j.source}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  const active = jobs.filter((j) => ['APPLIED', 'INTERVIEW'].includes(j.status));
  const events = jobs
    .flatMap((j) => {
      const events: { job: Job; date: string; type: 'interview' | 'followUp' }[] = [];
      if (j.interviewDate && j.status === 'INTERVIEW')
        events.push({ job: j, date: j.interviewDate, type: 'interview' });
      const due = followUp(j);
      if (due) events.push({ job: j, date: due, type: 'followUp' });
      return events;
    })
    .sort((a, b) => a.date.localeCompare(b.date));
  const upcoming = events.filter(
    (e) => e.type === 'followUp' || new Date(e.date).getTime() >= Date.now(),
  );
  const statusName = (s: Status) => t[statusKeys[s]];
  const notify = (message: string) => setToast(message);
  const money = (amount: number) =>
    new Intl.NumberFormat('de-CH', { maximumFractionDigits: 0 }).format(amount);
  const salary = (job: Job) =>
    job.salaryMin || job.salaryMax
      ? `CHF ${job.salaryMin ? money(job.salaryMin) : '…'}${job.salaryMax ? `–${money(job.salaryMax)}` : '+'}`
      : '—';
  function navigate(value: Page) {
    setPage(value);
    setMobileNav(false);
    setQuery('');
    setFilter('all');
  }
  function openJob(job: Job, nextTab: DetailTab = 'details') {
    setSelectedId(job.id);
    setTab(nextTab);
  }
  async function loadWorkspace(user: Account) {
    const [j, p, d] = await Promise.all([
      api<Job[]>('/jobs'),
      api<Profile[]>('/profiles'),
      api<Document[]>('/documents'),
    ]);
    setJobs(j);
    setProfiles(p);
    setDocuments(d);
    setAccount(user);
  }
  useEffect(() => {
    const saved = localStorage.getItem('applypilot-language');
    if (saved && saved in messages) setLang(saved as Language);
    let active = true;
    (async () => {
      try {
        const c = await api<Config>('/config');
        if (!active) return;
        setConfig(c);
        const verification = new URLSearchParams(location.search).get('verify');
        if (verification) {
          try {
            await api('/email/verify', 'POST', { token: verification });
            setToast('Email verified. Reminders are enabled.');
          } catch (error) {
            setToast((error as Error).message);
          } finally {
            history.replaceState(null, '', '/');
          }
        }
        const response = await fetch('/api/auth/me', { cache: 'no-store' });
        if (response.ok) {
          const user = await response.json();
          if (active) await loadWorkspace(user);
        } else if (response.status !== 401) throw new Error('Could not load your account.');
      } catch {
        if (active) setToast('Could not connect to the server. You are viewing sample data.');
      } finally {
        if (active) setLoading(false);
      }
    })();
    if (new URLSearchParams(location.search).has('authError'))
      setToast(
        'Google sign-in could not be completed. An existing email account must use its password.',
      );
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    document.documentElement.lang = lang;
    localStorage.setItem('applypilot-language', lang);
  }, [lang]);
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(''), 6000);
    return () => clearTimeout(timer);
  }, [toast]);
  async function act(action: () => Promise<void>) {
    setBusy(true);
    try {
      await action();
    } catch (e) {
      notify((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function updateStatus(job: Job, status: Status) {
    await act(async () => {
      const changed = demo
        ? {
            ...job,
            status,
            appliedDate: job.appliedDate || (status !== 'INTERESTED' ? swissToday() : null),
          }
        : await api<Job>(`/jobs/${job.id}`, 'PUT', {
            version: job.version,
            data: { ...jobData(job), status },
          });
      setJobs((all) => all.map((j) => (j.id === job.id ? changed : j)));
      notify(demo ? t.demoChange : t.saved);
    });
  }
  async function runAI(kind: Exclude<DetailTab, 'details'>) {
    if (!selected) return;
    if (demo) {
      setAuth('register');
      return;
    }
    if (!config.ai) {
      notify(t.aiUnavailable);
      return;
    }
    if (
      !selected.description.trim() ||
      !profiles.find((p) => p.id === selected.profileId)?.text.trim()
    ) {
      notify(t.aiNeed);
      return;
    }
    await act(async () => {
      const result = await api<Job>(`/jobs/${selected.id}/ai`, 'POST', { kind });
      setJobs((all) => all.map((j) => (j.id === result.id ? result : j)));
      notify(t.saved);
    });
  }
  function upload(entryId: string | null = null) {
    if (demo) {
      setAuth('register');
      return;
    }
    uploadEntry.current = entryId;
    fileInput.current?.click();
  }
  function deleteJob(job: Job) {
    setConfirmation({
      text: t.confirmDelete,
      action: async () => {
        if (!demo) await api(`/jobs/${job.id}`, 'DELETE');
        setJobs((all) => all.filter((j) => j.id !== job.id));
        setDocuments((all) => all.filter((d) => d.entryId !== job.id));
        setSelectedId(null);
        notify(demo ? t.demoChange : t.deleted);
      },
    });
  }
  function deleteProfile(profile: Profile) {
    setConfirmation({
      text: t.deleteProfile,
      action: async () => {
        if (jobs.some((j) => j.profileId === profile.id))
          throw new Error('Remove this CV profile from linked applications first.');
        if (!demo) await api(`/profiles/${profile.id}`, 'DELETE');
        setProfiles((all) => all.filter((p) => p.id !== profile.id));
        notify(demo ? t.demoChange : t.deleted);
      },
    });
  }
  function deleteDocument(doc: Document) {
    setConfirmation({
      text: t.deleteDocument,
      action: async () => {
        await api(`/documents/${doc.id}`, 'DELETE');
        setDocuments((all) => all.filter((d) => d.id !== doc.id));
        notify(t.deleted);
      },
    });
  }
  async function checkout(plan: string) {
    if (demo) {
      setAuth('register');
      return;
    }
    await act(async () => {
      const data = await api<{ url: string }>('/billing/checkout', 'POST', { plan });
      window.location.assign(data.url);
    });
  }
  const logo = (
    <span className="brand-mark" aria-hidden="true">
      <svg viewBox="0 0 32 32">
        <path d="m7 25 9-20h4l9 20h-7l-4-10-4 10Z" fill="currentColor" />
        <path d="m4 13 14 5-14 3Z" fill="#a8dbc0" />
      </svg>
    </span>
  );
  function Badge({ status }: { status: Status }) {
    return (
      <span className={`badge status-${status.toLowerCase()}`}>
        <i />
        {statusName(status)}
      </span>
    );
  }
  function CompanyIcon({ job }: { job: Job }) {
    return (
      <span className={`company-icon company-${job.company.charCodeAt(0) % 5}`}>
        {job.company
          .split(' ')
          .map((p) => p[0])
          .slice(0, 2)
          .join('')}
      </span>
    );
  }
  function Empty({
    title,
    note,
    action,
  }: {
    title: string;
    note?: string;
    action?: React.ReactNode;
  }) {
    return (
      <div className="empty">
        <div className="empty-icon">
          <BriefcaseBusiness size={26} />
        </div>
        <h3>{title}</h3>
        {note && <p>{note}</p>}
        {action}
      </div>
    );
  }
  function JobTable({ items }: { items: Job[] }) {
    return items.length ? (
      <div className="table-scroll">
        <table className="jobs-table">
          <thead>
            <tr>
              <th>{t.company}</th>
              <th>{t.status}</th>
              <th>{t.salary}</th>
              <th>{t.updated}</th>
              <th>
                <span className="sr-only">{t.actions}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {items.map((job) => (
              <tr key={job.id}>
                <td>
                  <button className="company-cell" onClick={() => openJob(job)}>
                    <CompanyIcon job={job} />
                    <span>
                      <strong>{job.title}</strong>
                      <small>
                        {job.company}
                        <span>·</span>
                        {job.location || 'Switzerland'}
                      </small>
                    </span>
                  </button>
                </td>
                <td>
                  <Badge status={job.status} />
                </td>
                <td className="salary-cell">
                  {salary(job)}
                  <small>{job.workload ? `${job.workload}%` : ''}</small>
                </td>
                <td className="muted">{dateLabel(job.appliedDate, lang)}</td>
                <td>
                  <button
                    className="icon-button"
                    aria-label={`${t.viewApplication}: ${job.title}`}
                    onClick={() => openJob(job)}
                  >
                    <ArrowUpRight size={17} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    ) : (
      <Empty
        title={query ? t.noResults : t.empty}
        note={query ? '' : t.emptyNote}
        action={
          query ? (
            <Button variant="outline" onClick={() => setQuery('')}>
              {t.clearSearch}
            </Button>
          ) : (
            <Button onClick={() => setJobForm(null)}>
              <Plus size={16} />
              {t.add}
            </Button>
          )
        }
      />
    );
  }
  function EventCard({ event }: { event: (typeof events)[number] }) {
    const { job, date, type } = event;
    const overdue = type === 'followUp' && date <= swissToday();
    return (
      <div className={`event-card ${type}`}>
        <div className="event-top">
          <span className="event-type">
            {type === 'interview' ? <Users size={14} /> : <Clock3 size={14} />} {t[type]}
          </span>
          <small className={overdue ? 'due' : ''}>{overdue ? t.due : dateLabel(date, lang)}</small>
        </div>
        <h4>{job.company}</h4>
        <p>{job.title}</p>
        {type === 'interview' && (
          <div className="event-time">
            <CalendarDays size={13} />
            {dateLabel(date, lang, true)}
          </div>
        )}
        <button
          className="event-action"
          onClick={() => openJob(job, type === 'interview' ? 'interviewPrep' : 'details')}
        >
          {type === 'interview' ? t.prepare : t.viewApplication}
          <ArrowRight size={15} />
        </button>
      </div>
    );
  }
  function DocumentList({ items }: { items: Document[] }) {
    return items.length ? (
      <div className="document-list">
        {items.map((doc) => (
          <div className="document-row" key={doc.id}>
            <span className="document-icon">
              <FileText size={21} />
            </span>
            <div>
              <strong>{doc.filename}</strong>
              <small>
                {(doc.size / 1024).toFixed(0)} KB · {dateLabel(doc.createdAt, lang)}
              </small>
            </div>
            <a
              className="icon-button"
              aria-label={`${t.download} ${doc.filename}`}
              href={`/api/documents/${doc.id}`}
            >
              <ArrowDownToLine size={17} />
            </a>
            <button
              className="icon-button"
              aria-label={`${t.delete} ${doc.filename}`}
              onClick={() => deleteDocument(doc)}
            >
              <Trash2 size={16} />
            </button>
          </div>
        ))}
      </div>
    ) : (
      <Empty title={t.noDocuments} note={t.uploadHint} />
    );
  }
  function AIContent({ kind }: { kind: Exclude<DetailTab, 'details'> }) {
    if (!selected) return null;
    const result = selected[kind];
    const title =
      kind === 'analysis'
        ? t.noAnalysis
        : kind === 'coverLetter'
          ? t.noLetter
          : kind === 'interviewPrep'
            ? t.noPrep
            : t.noTailoring;
    return (
      <div className="ai-section">
        <div className="ai-intro">
          <span className="ai-icon">
            <Sparkles size={22} />
          </span>
          <div>
            <h3>{title}</h3>
            <p>{t.honestNote}</p>
          </div>
        </div>
        {result ? (
          <>
            <p className="analysis-summary">{result.summary}</p>
            {kind === 'analysis' && (
              <>
                <div className="match-count">
                  <strong>
                    {result.requirements.filter((r) => r.status === 'matched').length}
                    <span> / {result.requirements.length}</span>
                  </strong>
                  <span>{t.matched}</span>
                  <div className="match-track">
                    <i
                      style={{
                        width: `${result.requirements.length ? (result.requirements.filter((r) => r.status === 'matched').length / result.requirements.length) * 100 : 0}%`,
                      }}
                    />
                  </div>
                </div>
                <div className="requirements">
                  {result.requirements.map((r, i) => (
                    <div className={`requirement ${r.status}`} key={i}>
                      <span className="requirement-symbol">
                        {r.status === 'matched' ? (
                          <Check size={15} />
                        ) : r.status === 'missing' ? (
                          <X size={15} />
                        ) : (
                          <MoreHorizontal size={15} />
                        )}
                      </span>
                      <div>
                        <strong>{r.requirement}</strong>
                        {r.evidence && (
                          <p>
                            {t.evidence}: “{r.evidence}”
                          </p>
                        )}
                        {r.suggestion && <p>{r.suggestion}</p>}
                      </div>
                      <small>
                        {r.status === 'matched'
                          ? t.evidence
                          : r.status === 'partial'
                            ? t.partial
                            : t.missing}
                      </small>
                    </div>
                  ))}
                </div>
              </>
            )}
            {kind === 'coverLetter' && (
              <>
                <div className="draft-label">{t.review}</div>
                <div className="letter-paper">{result.letter}</div>
                <div className="inline-actions">
                  <Button
                    variant="outline"
                    onClick={() =>
                      downloadText(`${selected.company}-cover-letter.txt`, result.letter)
                    }
                  >
                    <ArrowDownToLine size={16} />
                    {t.download}
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => {
                      document.body.dataset.print = 'letter';
                      window.print();
                      delete document.body.dataset.print;
                    }}
                  >
                    <FileText size={16} />
                    {t.print}
                  </Button>
                </div>
              </>
            )}
            {kind === 'interviewPrep' &&
              [
                [t.prepTopics, result.topics],
                [t.prepQuestions, result.questions],
                [t.prepGaps, result.weaknesses],
              ].map(([label, items]) => (
                <div className="prep-block" key={label as string}>
                  <h4>{label as string}</h4>
                  <ol>
                    {(items as string[]).map((item, i) => (
                      <li key={i}>{item}</li>
                    ))}
                  </ol>
                </div>
              ))}
            {kind === 'tailoring' && (
              <ol className="changes">
                {result.changes.map((change, i) => (
                  <li key={i}>{change}</li>
                ))}
              </ol>
            )}
          </>
        ) : (
          <div className="ai-empty">
            <Target size={42} strokeWidth={1.3} />
            <p>{kind === 'analysis' ? t.analysisNote : t.honestNote}</p>
          </div>
        )}
        <div className="ai-footer">
          <p>
            <ShieldCheck size={15} />
            {demo ? t.aiDemo : config.ai ? t.aiConsent : t.aiUnavailable}
          </p>
          <Button disabled={busy || (!demo && !config.ai)} onClick={() => runAI(kind)}>
            {busy ? <LoaderCircle size={16} className="spin" /> : <Sparkles size={16} />}{' '}
            {kind === 'analysis' ? t.analyze : t.generate}
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="app-shell">
      {mobileNav && (
        <button className="nav-scrim" aria-label={t.close} onClick={() => setMobileNav(false)} />
      )}
      <aside className={`sidebar ${mobileNav ? 'mobile-open' : ''}`}>
        <button className="brand" onClick={() => navigate('overview')}>
          {logo}
          <span>
            Apply<span className="brand-light">Pilot</span>
            <span className="brand-dot">.</span>
          </span>
        </button>
        <div className="workspace-switch">
          <span className="workspace-avatar">{account ? account.name[0].toUpperCase() : 'Y'}</span>
          <span>
            <strong>
              {account
                ? account.name
                : t.workspace.replace('YOUR ', 'Your ').replace('WORKSPACE', 'workspace')}
            </strong>
            <small>{demo ? 'Personal workspace' : account.plan.toLowerCase() + ' workspace'}</small>
          </span>
          <span className="swiss-flag" title="Switzerland">
            +
          </span>
        </div>
        <span className="nav-label">{t.workspace}</span>
        <nav aria-label={t.workspace}>
          {navItems.map((item) => (
            <button
              className={`nav-item ${page === item.id ? 'active' : ''}`}
              key={item.id}
              onClick={() => navigate(item.id)}
            >
              <item.icon size={19} />
              <span>{t[item.id]}</span>
              {item.id === 'applications' && <span className="nav-count">{jobs.length}</span>}
              {item.id === 'calendar' && upcoming.length > 0 && <i className="nav-dot" />}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-note">
            <span className="little-spark">
              <Sparkles size={17} />
            </span>
            <h4>{t.brandNote}</h4>
            <p>{t.swiss}</p>
            <div className="mountain-line">
              <span />
              <span />
              <span />
            </div>
          </div>
          <button
            className={`nav-item ${page === 'settings' ? 'active' : ''}`}
            onClick={() => navigate('settings')}
          >
            <Settings2 size={19} />
            {t.settings}
          </button>
          <div className="sidebar-account">
            <span className="user-avatar">
              <UserRound size={18} />
            </span>
            <div>
              <strong>{account?.name || 'Your workspace'}</strong>
              <small>{account?.email || 'Switzerland · CHF'}</small>
            </div>
            {account ? (
              <button
                className="icon-button"
                aria-label={t.signOut}
                onClick={() =>
                  act(async () => {
                    await api('/auth/logout', 'POST');
                    await refreshCsrf();
                    setAccount(null);
                    setJobs(demoJobs);
                    setProfiles(demoProfiles);
                    setDocuments([]);
                    setSelectedId(null);
                    navigate('overview');
                  })
                }
              >
                <LogOut size={17} />
              </button>
            ) : (
              <button
                className="icon-button"
                aria-label={t.signIn}
                onClick={() => setAuth('login')}
              >
                <ArrowRight size={17} />
              </button>
            )}
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            <button
              className="icon-button mobile-menu"
              aria-label={t.menu}
              onClick={() => setMobileNav(true)}
            >
              <Menu size={21} />
            </button>
            <span>Workspace</span>
            <ChevronRight size={14} />
            <strong>{t[page]}</strong>
          </div>
          <div className="topbar-actions">
            <label className="global-search">
              <Search size={16} />
              <input
                aria-label={t.search}
                placeholder={t.search}
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  if (page !== 'applications') setPage('applications');
                }}
              />
              <span>⌕</span>
            </label>
            <label className="language-switch">
              <Globe2 size={16} />
              <select
                value={lang}
                aria-label={t.language}
                onChange={(e) => setLang(e.target.value as Language)}
              >
                {Object.keys(languageNames).map((l) => (
                  <option key={l} value={l}>
                    {l.toUpperCase()}
                  </option>
                ))}
              </select>
            </label>
            <button
              className="notification-button icon-button"
              aria-label={t.nextUp}
              onClick={() => navigate('calendar')}
            >
              <Bell size={19} />
              {upcoming.length > 0 && <i />}
            </button>
            <span className="top-avatar">{account?.name[0].toUpperCase() || 'Y'}</span>
          </div>
        </header>
        {demo && !loading && (
          <div className="preview-banner">
            <span>
              <strong>{t.preview}</strong>
              <span className="preview-description">{t.previewNote}</span>
            </span>
            <button onClick={() => setAuth('register')}>
              {t.createAccount}
              <ArrowRight size={14} />
            </button>
          </div>
        )}
        <main>
          <div className="page-heading">
            <div>
              <div className="eyebrow">
                {page === 'overview'
                  ? new Intl.DateTimeFormat(locale[lang], {
                      weekday: 'long',
                      day: 'numeric',
                      month: 'long',
                      timeZone: 'Europe/Zurich',
                    }).format(new Date())
                  : 'APPLYPILOT / ' + t[page].toUpperCase()}
              </div>
              <h1>{page === 'overview' ? t.greeting : t[page]}</h1>
              <p>
                {page === 'overview'
                  ? t.subtitle
                  : page === 'profiles'
                    ? t.profileNote
                    : page === 'calendar'
                      ? t.calendarNote
                      : page === 'insights'
                        ? t.insightsNote
                        : page === 'settings'
                          ? t.settingsNote
                          : page === 'documents'
                            ? t.privacy
                            : `${jobs.length} ${t.applications.toLowerCase()} · ${active.length} ${t.active.toLowerCase()}`}
              </p>
            </div>
            {['overview', 'applications'].includes(page) ? (
              <Button onClick={() => setJobForm(null)}>
                <Plus size={18} />
                {t.add}
              </Button>
            ) : page === 'profiles' ? (
              <Button onClick={() => setProfileForm(null)}>
                <Plus size={18} />
                {t.addProfile}
              </Button>
            ) : page === 'documents' ? (
              <Button onClick={() => upload()}>
                <Plus size={18} />
                {t.upload}
              </Button>
            ) : null}
          </div>
          {loading && <div className="loading-bar" aria-label={t.busy} />}
          {page === 'overview' && (
            <>
              <div className="stats-grid">
                {[
                  {
                    label: t.total,
                    value: jobs.length,
                    icon: BriefcaseBusiness,
                    note: t.all,
                    color: 'green',
                  },
                  {
                    label: t.active,
                    value: active.length,
                    icon: ArrowUpRight,
                    note: t.applied + ' + ' + t.interview,
                    color: 'blue',
                  },
                  {
                    label: t.interviews,
                    value: counts.INTERVIEW,
                    icon: Users,
                    note: t.nextAction,
                    color: 'purple',
                  },
                  {
                    label: t.offers,
                    value: counts.OFFER,
                    icon: CheckCheck,
                    note: t.offer,
                    color: 'gold',
                  },
                ].map((stat, i) => (
                  <button
                    className="stat-card"
                    key={stat.label}
                    onClick={() => {
                      navigate('applications');
                      setFilter(
                        i === 2 ? 'INTERVIEW' : i === 3 ? 'OFFER' : i === 1 ? 'active' : 'all',
                      );
                    }}
                  >
                    <div>
                      <span>{stat.label}</span>
                      <span className={`stat-icon ${stat.color}`}>
                        <stat.icon size={18} />
                      </span>
                    </div>
                    <strong>{stat.value.toString().padStart(2, '0')}</strong>
                    <small>
                      {i === 0 ? (
                        <span className="tiny-trend">
                          <TrendingUp size={13} />
                        </span>
                      ) : (
                        <span className={`tiny-dot ${stat.color}`} />
                      )}{' '}
                      {stat.note}
                    </small>
                  </button>
                ))}
              </div>
              <div className="overview-layout">
                <div className="overview-main">
                  <section className="panel pipeline-panel">
                    <div className="panel-heading">
                      <h2>{t.pipeline}</h2>
                      <span className="quiet-label">
                        {jobs.length} {t.applications.toLowerCase()}
                      </span>
                    </div>
                    <div className="pipeline-track">
                      {statuses.map((s) => (
                        <button
                          title={statusName(s)}
                          key={s}
                          className={`pipeline-segment status-${s.toLowerCase()}`}
                          style={{ flex: Math.max(counts[s], 0.4) }}
                          onClick={() => {
                            navigate('applications');
                            setFilter(s);
                          }}
                        />
                      ))}
                    </div>
                    <div className="pipeline-labels">
                      {statuses.map((s) => (
                        <button
                          key={s}
                          onClick={() => {
                            navigate('applications');
                            setFilter(s);
                          }}
                        >
                          <span>
                            <i className={`stage-dot status-${s.toLowerCase()}`} />
                            {statusName(s)}
                          </span>
                          <strong>{counts[s]}</strong>
                        </button>
                      ))}
                    </div>
                  </section>
                  <section className="panel">
                    <div className="panel-heading">
                      <h2>{t.recent}</h2>
                      <button className="text-link" onClick={() => navigate('applications')}>
                        {t.viewAll}
                        <ArrowRight size={14} />
                      </button>
                    </div>
                    <JobTable items={jobs.slice(0, 5)} />
                    <div className="panel-foot">
                      <ShieldCheck size={14} />
                      {t.privacy}
                    </div>
                  </section>
                  <div className="workflow-note">
                    <span className="note-icon">
                      <Target size={23} />
                    </span>
                    <div>
                      <h3>{t.honest}</h3>
                      <p>{t.honestNote}</p>
                    </div>
                    <Button variant="ghost" onClick={() => navigate('profiles')}>
                      {t.profiles}
                      <ArrowRight size={16} />
                    </Button>
                  </div>
                </div>
                <aside className="next-panel">
                  <div className="next-heading">
                    <span className="next-icon">
                      <CalendarDays size={19} />
                    </span>
                    <h2>{t.nextUp}</h2>
                    <span>{upcoming.length}</span>
                  </div>
                  <p>{t.nextNote}</p>
                  {upcoming.length ? (
                    upcoming
                      .slice(0, 3)
                      .map((e, i) => <EventCard key={e.job.id + e.type + i} event={e} />)
                  ) : (
                    <div className="no-events">
                      <CalendarDays size={30} />
                      <p>{t.noEvents}</p>
                    </div>
                  )}
                  <button className="calendar-link" onClick={() => navigate('calendar')}>
                    {t.calendar}
                    <ArrowUpRight size={15} />
                  </button>
                  <div className="next-footer">
                    <span className="swiss-flag">+</span> Europe / Zurich <Clock3 size={12} />
                  </div>
                </aside>
              </div>
            </>
          )}
          {page === 'applications' && (
            <>
              <div className="applications-toolbar">
                <label className="mobile-application-search">
                  <Search size={16} />
                  <input
                    aria-label={t.search}
                    placeholder={t.search}
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                  />
                </label>
                <div className="filter-tabs">
                  {filter === 'active' && (
                    <button className="active" onClick={() => setFilter('all')}>
                      {t.active}
                      <span>{active.length}</span>
                      <X size={12} />
                    </button>
                  )}
                  <button
                    className={filter === 'all' ? 'active' : ''}
                    onClick={() => setFilter('all')}
                  >
                    {t.all}
                    <span>{jobs.length}</span>
                  </button>
                  {statuses.map((s) => (
                    <button
                      key={s}
                      className={filter === s ? 'active' : ''}
                      onClick={() => setFilter(s)}
                    >
                      {statusName(s)}
                      <span>{counts[s]}</span>
                    </button>
                  ))}
                </div>
                <div className="view-toggle">
                  <button
                    className={!board ? 'active' : ''}
                    aria-label={t.list}
                    onClick={() => setBoard(false)}
                  >
                    <List size={17} />
                  </button>
                  <button
                    className={board ? 'active' : ''}
                    aria-label={t.board}
                    onClick={() => setBoard(true)}
                  >
                    <LayoutGrid size={16} />
                  </button>
                </div>
              </div>
              {query && (
                <div className="search-result">
                  <Search size={15} />
                  {query}
                  <button
                    className="icon-button"
                    aria-label={t.clearSearch}
                    onClick={() => setQuery('')}
                  >
                    <X size={14} />
                  </button>
                </div>
              )}
              {board ? (
                <div className="kanban">
                  {statuses
                    .filter(
                      (s) =>
                        filter === 'all' ||
                        (filter === 'active' ? ['APPLIED', 'INTERVIEW'].includes(s) : filter === s),
                    )
                    .map((s) => (
                      <section className="kanban-column" key={s}>
                        <h3>
                          <i className={`stage-dot status-${s.toLowerCase()}`} />
                          {statusName(s)}
                          <span>{filtered.filter((j) => j.status === s).length}</span>
                        </h3>
                        {filtered
                          .filter((j) => j.status === s)
                          .map((job) => (
                            <article className="kanban-card" key={job.id}>
                              <button className="kanban-open" onClick={() => openJob(job)}>
                                <CompanyIcon job={job} />
                                <ArrowUpRight size={16} />
                                <h4>{job.title}</h4>
                                <p>{job.company}</p>
                                <small>
                                  <MapPin size={12} />
                                  {job.location} · {job.workload}%
                                </small>
                                <strong className="kanban-salary">{salary(job)}</strong>
                              </button>
                              <select
                                aria-label={`${t.status}: ${job.title}`}
                                value={job.status}
                                disabled={busy}
                                onChange={(e) => updateStatus(job, e.target.value as Status)}
                              >
                                {statuses.map((s) => (
                                  <option key={s} value={s}>
                                    {statusName(s)}
                                  </option>
                                ))}
                              </select>
                            </article>
                          ))}
                        <button className="kanban-add" onClick={() => setJobForm(null)}>
                          <Plus size={15} />
                          {t.add}
                        </button>
                      </section>
                    ))}
                </div>
              ) : (
                <section className="panel">
                  <JobTable items={filtered} />
                </section>
              )}
            </>
          )}
          {page === 'profiles' && (
            <div className="profiles-grid">
              {profiles.map((profile) => (
                <article className="profile-card" key={profile.id}>
                  <div className="profile-top">
                    <span className="profile-icon">
                      <FileText size={25} />
                    </span>
                    <span className="language-tag">{profile.language.toUpperCase()}</span>
                  </div>
                  <h2>{profile.name}</h2>
                  <p>
                    {profile.text.slice(0, 170)}
                    {profile.text.length > 170 ? '…' : ''}
                  </p>
                  <div className="profile-meta">
                    <BriefcaseBusiness size={14} />
                    {jobs.filter((j) => j.profileId === profile.id).length} {t.linked.toLowerCase()}
                  </div>
                  <div className="profile-bottom">
                    <Button variant="outline" size="sm" onClick={() => setProfileForm(profile)}>
                      {t.profiles}
                      <ArrowUpRight size={14} />
                    </Button>
                    <button
                      className="icon-button"
                      aria-label={t.delete}
                      onClick={() => deleteProfile(profile)}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </article>
              ))}
              <button className="add-profile-card" onClick={() => setProfileForm(null)}>
                <span>
                  <Plus size={25} />
                </span>
                <strong>{t.addProfile}</strong>
                <p>{t.profileNote}</p>
              </button>
            </div>
          )}
          {page === 'documents' && (
            <section className="panel">
              <div className="panel-heading">
                <h2>{t.allDocuments}</h2>
                <span className="quiet-label">{documents.length}</span>
              </div>
              <DocumentList items={documents} />
            </section>
          )}
          {page === 'calendar' && (
            <section className="panel calendar-panel">
              {events.length ? (
                events.map((e, i) => (
                  <div className="calendar-row" key={e.job.id + e.type + i}>
                    <div className="calendar-date">
                      <strong>
                        {new Date(
                          e.date.length === 10 ? e.date + 'T12:00:00Z' : e.date,
                        ).toLocaleDateString(locale[lang], {
                          day: '2-digit',
                          timeZone: 'Europe/Zurich',
                        })}
                      </strong>
                      <span>
                        {new Date(
                          e.date.length === 10 ? e.date + 'T12:00:00Z' : e.date,
                        ).toLocaleDateString(locale[lang], {
                          month: 'short',
                          timeZone: 'Europe/Zurich',
                        })}
                      </span>
                    </div>
                    <div className="calendar-event">
                      <span className={`event-type ${e.type}`}>{t[e.type]}</span>
                      <h3>{e.job.title}</h3>
                      <p>
                        {e.job.company} ·{' '}
                        {e.type === 'interview' ? dateLabel(e.date, lang, true) : t.followUp}
                      </p>
                    </div>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() =>
                        openJob(e.job, e.type === 'interview' ? 'interviewPrep' : 'details')
                      }
                    >
                      {e.type === 'interview' ? t.prepare : t.viewApplication}
                      <ArrowRight size={14} />
                    </Button>
                    {e.type === 'interview' && (
                      <button
                        className="icon-button"
                        aria-label={t.exportCalendar}
                        onClick={() =>
                          downloadText(
                            `${e.job.company}-interview.ics`,
                            calendarFile(e.job),
                            'text/calendar',
                          )
                        }
                      >
                        <ArrowDownToLine size={17} />
                      </button>
                    )}
                  </div>
                ))
              ) : (
                <Empty title={t.noEvents} />
              )}
            </section>
          )}
          {page === 'insights' && (
            <div className="insights-grid">
              <section className="panel insights-summary">
                <span className="stat-icon green">
                  <TrendingUp size={23} />
                </span>
                <h2>{t.responseRate}</h2>
                <strong>
                  {jobs.filter((j) => j.appliedDate).length
                    ? Math.round(
                        (jobs.filter(
                          (j) => ['INTERVIEW', 'OFFER'].includes(j.status) && j.appliedDate,
                        ).length /
                          jobs.filter((j) => j.appliedDate).length) *
                          100,
                      )
                    : 0}
                  <span>%</span>
                </strong>
                <p>
                  {t.interview} + {t.offer} / {t.applied}
                </p>
              </section>
              <section className="panel chart-panel">
                <h2>{t.stage}</h2>
                {statuses.map((s) => (
                  <div className="chart-row" key={s}>
                    <span>{statusName(s)}</span>
                    <div>
                      <i
                        className={`status-${s.toLowerCase()}`}
                        style={{ width: `${jobs.length ? (counts[s] / jobs.length) * 100 : 0}%` }}
                      />
                    </div>
                    <strong>{counts[s]}</strong>
                  </div>
                ))}
              </section>
              <section className="panel chart-panel sources-panel">
                <h2>{t.sources}</h2>
                {Array.from(new Set(jobs.map((j) => j.source))).map((source) => (
                  <div className="chart-row" key={source}>
                    <span>{source}</span>
                    <div>
                      <i
                        style={{
                          width: `${(jobs.filter((j) => j.source === source).length / Math.max(jobs.length, 1)) * 100}%`,
                        }}
                      />
                    </div>
                    <strong>{jobs.filter((j) => j.source === source).length}</strong>
                  </div>
                ))}
                {!jobs.length && <p>{t.noData}</p>}
              </section>
            </div>
          )}
          {page === 'settings' && (
            <>
              <section className="panel settings-panel">
                <div>
                  <h2>{t.account}</h2>
                  <p>{account ? `${account.name} · ${account.email}` : t.preview}</p>
                </div>
                <div className="settings-row">
                  <label className="field">
                    {t.language}
                    <select value={lang} onChange={(e) => setLang(e.target.value as Language)}>
                      {Object.entries(languageNames).map(([id, name]) => (
                        <option value={id} key={id}>
                          {name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <p>CHF · Europe/Zurich</p>
                </div>
                <div className="settings-row">
                  <div>
                    <h3>
                      {
                        {
                          en: 'Email reminders',
                          de: 'Erinnerungen per E-Mail',
                          fr: 'Rappels par e-mail',
                          pl: 'Przypomnienia e-mail',
                        }[lang]
                      }
                    </h3>
                    <p>
                      {
                        {
                          en: 'A daily summary of due follow-ups and upcoming interviews. Verify your email before reminders start.',
                          de: 'Eine tägliche Übersicht fälliger Rückmeldungen und bevorstehender Gespräche. Bestätige zuerst deine E-Mail.',
                          fr: 'Un résumé quotidien des relances et entretiens. Vérifiez votre e-mail pour activer les rappels.',
                          pl: 'Codzienne podsumowanie przypomnień i rozmów. Najpierw potwierdź adres e-mail.',
                        }[lang]
                      }
                    </p>
                  </div>
                  <Button
                    variant="outline"
                    disabled={busy || !config.email}
                    onClick={() => {
                      if (!account) {
                        setAuth('register');
                        return;
                      }
                      void act(async () => {
                        const result = await api<Account & { verificationSent?: boolean }>(
                          '/email/reminders',
                          'POST',
                          { enabled: !account.emailReminders, language: lang },
                        );
                        if (result.verificationSent)
                          notify(
                            {
                              en: 'Check your email to confirm reminders.',
                              de: 'Bestätige die Erinnerungen in deinem E-Mail-Postfach.',
                              fr: 'Consultez votre e-mail pour confirmer les rappels.',
                              pl: 'Sprawdź pocztę, aby potwierdzić przypomnienia.',
                            }[lang],
                          );
                        else {
                          setAccount(result);
                          notify(t.saved);
                        }
                      });
                    }}
                  >
                    {!config.email
                      ? t.unavailable
                      : account?.emailReminders
                        ? { en: 'Turn off', de: 'Ausschalten', fr: 'Désactiver', pl: 'Wyłącz' }[
                            lang
                          ]
                        : {
                            en: 'Enable reminders',
                            de: 'Erinnerungen aktivieren',
                            fr: 'Activer les rappels',
                            pl: 'Włącz przypomnienia',
                          }[lang]}
                  </Button>
                </div>
                <div className="settings-row">
                  <div>
                    <h3>{t.export}</h3>
                    <p>{t.exportNote}</p>
                  </div>
                  <Button
                    variant="outline"
                    onClick={() =>
                      act(async () => {
                        const data = demo
                          ? { applications: jobs, profiles, documents }
                          : await api('/export');
                        downloadText(
                          'applypilot-export.json',
                          JSON.stringify(data, null, 2),
                          'application/json',
                        );
                      })
                    }
                  >
                    <ArrowDownToLine size={16} />
                    {t.export}
                  </Button>
                </div>
              </section>
              <div className="plans-heading">
                <h2>{t.plans}</h2>
                {!config.billing && <p>{t.billingOff}</p>}
              </div>
              <div className="plans-grid">
                {[
                  {
                    name: 'FREE',
                    price: '0',
                    features: ['10 applications', 'Application tracker', 'CV profiles'],
                  },
                  {
                    name: 'PLUS',
                    price: '7.90',
                    features: [
                      'Unlimited applications',
                      'CV requirement analysis',
                      '100 AI requests / month',
                    ],
                  },
                  {
                    name: 'PRO',
                    price: '12.90',
                    features: [
                      'Everything in Plus',
                      'Letters & interview preparation',
                      '200 AI requests / month',
                    ],
                  },
                  {
                    name: 'LIFETIME',
                    price: '99',
                    features: ['Pro workflow features', '50 AI requests / month', 'One payment'],
                  },
                ].map((plan) => (
                  <section
                    className={`plan-card ${plan.name === 'PRO' ? 'featured' : ''}`}
                    key={plan.name}
                  >
                    <span>{plan.name}</span>
                    <h3>
                      <small>CHF</small> {plan.price}
                    </h3>
                    <p>{plan.name === 'LIFETIME' ? t.once : t.month}</p>
                    <ul>
                      {plan.features.map((f) => (
                        <li key={f}>
                          <Check size={14} />
                          {f}
                        </li>
                      ))}
                    </ul>
                    <Button
                      variant={plan.name === 'PRO' ? 'default' : 'outline'}
                      disabled={
                        !config.billing ||
                        busy ||
                        plan.name === 'FREE' ||
                        account?.plan === plan.name
                      }
                      onClick={() => checkout(plan.name)}
                    >
                      {account?.plan === plan.name
                        ? t.current
                        : config.billing
                          ? t.upgrade
                          : t.unavailable}
                    </Button>
                  </section>
                ))}
              </div>
              {config.billing && account && account.plan !== 'FREE' && (
                <Button
                  variant="outline"
                  onClick={() =>
                    act(async () => {
                      const result = await api<{ url: string }>('/billing/portal', 'POST');
                      location.assign(result.url);
                    })
                  }
                >
                  {t.manageBilling}
                  <ArrowUpRight size={16} />
                </Button>
              )}
            </>
          )}
          <footer className="workspace-footer">
            <span>{logo} ApplyPilot</span>
            <span>
              {t.swiss}
              <span className="swiss-flag">+</span>
            </span>
          </footer>
        </main>
      </div>
      {selected && (
        <Dialog
          open
          onOpenChange={(v) => !v && setSelectedId(null)}
          title={selected.title}
          description={`${selected.company} · ${selected.location} · ${selected.workload ?? 100}%`}
          wide
        >
          <div className="detail-header">
            <Badge status={selected.status} />
            <div>
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setJobForm(selected);
                  setSelectedId(null);
                }}
              >
                {t.edit}
              </Button>
              <button
                className="icon-button danger-text"
                aria-label={t.delete}
                onClick={() => deleteJob(selected)}
              >
                <Trash2 size={16} />
              </button>
            </div>
          </div>
          <div className="detail-tabs" role="tablist" aria-label={t.details}>
            {(['details', 'analysis', 'coverLetter', 'interviewPrep', 'tailoring'] as const).map(
              (key) => (
                <button
                  key={key}
                  id={`detail-tab-${key}`}
                  role="tab"
                  aria-controls="detail-panel"
                  tabIndex={tab === key ? 0 : -1}
                  aria-selected={tab === key}
                  onKeyDown={(e) => {
                    const keys: DetailTab[] = [
                      'details',
                      'analysis',
                      'coverLetter',
                      'interviewPrep',
                      'tailoring',
                    ];
                    const index = keys.indexOf(tab);
                    const next =
                      e.key === 'ArrowRight'
                        ? keys[(index + 1) % keys.length]
                        : e.key === 'ArrowLeft'
                          ? keys[(index + keys.length - 1) % keys.length]
                          : e.key === 'Home'
                            ? keys[0]
                            : e.key === 'End'
                              ? keys[keys.length - 1]
                              : null;
                    if (next) {
                      e.preventDefault();
                      setTab(next);
                      document.getElementById(`detail-tab-${next}`)?.focus();
                    }
                  }}
                  onClick={() => setTab(key)}
                >
                  {key === 'analysis' && <Sparkles size={14} />}{' '}
                  {key === 'coverLetter' ? t.letter : key === 'interviewPrep' ? t.prep : t[key]}
                </button>
              ),
            )}
          </div>
          <div role="tabpanel" id="detail-panel" aria-labelledby={`detail-tab-${tab}`}>
            {tab === 'details' ? (
              <>
                <div className="detail-grid">
                  <label className="field">
                    {t.status}
                    <select
                      value={selected.status}
                      disabled={busy}
                      onChange={(e) => updateStatus(selected, e.target.value as Status)}
                    >
                      {statuses.map((s) => (
                        <option value={s} key={s}>
                          {statusName(s)}
                        </option>
                      ))}
                    </select>
                  </label>
                  <div className="detail-fact">
                    <span>{t.salary}</span>
                    <strong>{salary(selected)}</strong>
                    <small>
                      {t.annual} · {selected.workload ?? 100}%
                    </small>
                  </div>
                  <div className="detail-fact">
                    <span>{t.updated}</span>
                    <strong>{dateLabel(selected.appliedDate, lang)}</strong>
                  </div>
                  <div className="detail-fact">
                    <span>{t.contactName}</span>
                    <strong>{selected.contactName || t.notSet}</strong>
                    {selected.contactEmail && (
                      <a href={`mailto:${selected.contactEmail}`}>{selected.contactEmail}</a>
                    )}
                  </div>
                </div>
                <div className="next-action-box">
                  <CalendarDays size={22} />
                  <div>
                    <span>{t.nextAction}</span>
                    <strong>
                      {selected.interviewDate && selected.status === 'INTERVIEW'
                        ? `${t.interview} · ${dateLabel(selected.interviewDate, lang, true)}`
                        : followUp(selected)
                          ? `${t.followUp} · ${dateLabel(followUp(selected), lang)}`
                          : t.noNextAction}
                    </strong>
                  </div>
                  {selected.interviewDate && selected.status === 'INTERVIEW' && (
                    <button
                      className="icon-button"
                      aria-label={t.exportCalendar}
                      onClick={() =>
                        downloadText('interview.ics', calendarFile(selected), 'text/calendar')
                      }
                    >
                      <ArrowDownToLine size={18} />
                    </button>
                  )}
                </div>
                <div className="detail-block">
                  <h3>{t.description}</h3>
                  {selected.url && (
                    <a
                      className="text-link"
                      href={selected.url}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      {selected.source}
                      <ArrowUpRight size={14} />
                    </a>
                  )}
                  <p className="preserve-whitespace">{selected.description || t.descriptionHint}</p>
                </div>
                {selected.notes && (
                  <div className="detail-block">
                    <h3>{t.notes}</h3>
                    <p className="preserve-whitespace">{selected.notes}</p>
                  </div>
                )}
                <div className="detail-block">
                  <div className="section-row">
                    <h3>{t.documents}</h3>
                    <Button variant="outline" size="sm" onClick={() => upload(selected.id)}>
                      <Plus size={14} />
                      {t.upload}
                    </Button>
                  </div>
                  <DocumentList items={documents.filter((d) => d.entryId === selected.id)} />
                </div>
              </>
            ) : (
              <AIContent kind={tab} />
            )}
          </div>
        </Dialog>
      )}
      {jobForm !== undefined && (
        <JobForm
          job={jobForm}
          profiles={profiles}
          lang={lang}
          onClose={() => setJobForm(undefined)}
          onSave={async (data) => {
            const result = demo
              ? {
                  ...data,
                  id: jobForm?.id ?? crypto.randomUUID(),
                  version: 0,
                  createdAt: jobForm?.createdAt ?? new Date().toISOString(),
                }
              : await api<Job>(
                  jobForm ? `/jobs/${jobForm.id}` : '/jobs',
                  jobForm ? 'PUT' : 'POST',
                  jobForm ? { version: jobForm.version, data } : data,
                );
            setJobs((all) =>
              jobForm ? all.map((j) => (j.id === jobForm.id ? result : j)) : [result, ...all],
            );
            notify(demo ? t.demoChange : t.saved);
          }}
        />
      )}
      {profileForm !== undefined && (
        <ProfileForm
          profile={profileForm}
          lang={lang}
          demo={demo}
          onClose={() => setProfileForm(undefined)}
          onUploaded={(doc) => setDocuments((all) => [doc, ...all])}
          onSave={async (data) => {
            const result = demo
              ? { ...data, id: profileForm?.id ?? crypto.randomUUID(), version: 0 }
              : await api<Profile>(
                  profileForm ? `/profiles/${profileForm.id}` : '/profiles',
                  profileForm ? 'PUT' : 'POST',
                  profileForm ? { version: profileForm.version, data } : data,
                );
            setProfiles((all) =>
              profileForm
                ? all.map((p) => (p.id === profileForm.id ? result : p))
                : [result, ...all],
            );
            if (profileForm)
              setJobs((all) =>
                all.map((j) =>
                  j.profileId === profileForm.id
                    ? {
                        ...j,
                        analysis: undefined,
                        coverLetter: undefined,
                        interviewPrep: undefined,
                        tailoring: undefined,
                      }
                    : j,
                ),
              );
            if (!demo && profileForm) setJobs(await api<Job[]>('/jobs'));
            notify(demo ? t.demoChange : t.profileSaved);
          }}
        />
      )}
      {auth && (
        <AuthForm
          mode={auth}
          lang={lang}
          config={config}
          onMode={setAuth}
          onSuccess={loadWorkspace}
          onClose={() => setAuth(null)}
        />
      )}
      {confirmation && (
        <Dialog
          open
          onOpenChange={(v) => !v && setConfirmation(null)}
          title={t.delete}
          description={confirmation.text}
        >
          <div className="form-footer">
            <Button variant="outline" onClick={() => setConfirmation(null)}>
              {t.cancel}
            </Button>
            <Button
              variant="destructive"
              disabled={busy}
              onClick={() =>
                act(async () => {
                  await confirmation.action();
                  setConfirmation(null);
                })
              }
            >
              {busy ? t.busy : t.delete}
            </Button>
          </div>
        </Dialog>
      )}
      <input
        ref={fileInput}
        type="file"
        className="sr-only"
        accept=".pdf,.txt"
        onChange={(e) => {
          const file = e.target.files?.[0];
          e.target.value = '';
          if (!file) return;
          void act(async () => {
            const form = new FormData();
            form.set('file', file);
            if (uploadEntry.current) form.set('entryId', uploadEntry.current);
            const result = await api<Document>('/documents', 'POST', form);
            setDocuments((all) => [result, ...all]);
            notify(t.saved);
          });
        }}
      />
      {toast && (
        <div className="toast" role="status">
          <CircleHelp size={18} />
          <span>{toast}</span>
          <button className="icon-button" onClick={() => setToast('')} aria-label={t.close}>
            <X size={16} />
          </button>
        </div>
      )}
    </div>
  );
}
