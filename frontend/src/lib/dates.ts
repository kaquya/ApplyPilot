import { locale } from './i18n';
import type { Job, Language } from './types';
export const swissToday = () =>
  new Date().toLocaleDateString('en-CA', { timeZone: 'Europe/Zurich' });
export function dateLabel(value: string | null | undefined, lang: Language, withTime = false) {
  if (!value) return '—';
  return new Intl.DateTimeFormat(locale[lang], {
    day: 'numeric',
    month: 'short',
    ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}),
    timeZone: 'Europe/Zurich',
  }).format(new Date(value.length === 10 ? value + 'T12:00:00Z' : value));
}
export function swissLocal(value: string | null) {
  if (!value) return '';
  const parts = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Europe/Zurich',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(new Date(value));
  return parts.replace(' ', 'T');
}
export function fromSwissLocal(value: string): string | null {
  if (!value) return null;
  const target = Date.parse(value + 'Z');
  let candidate = target;
  for (let i = 0; i < 3; i++) {
    const local = Date.parse(swissLocal(new Date(candidate).toISOString()) + 'Z');
    candidate += target - local;
  }
  const result = new Date(candidate).toISOString();
  if (swissLocal(result) !== value)
    throw new Error(
      'This local time does not exist because of daylight saving. Choose another time.',
    );
  return result;
}
export function followUp(job: Job) {
  if (!['APPLIED', 'INTERVIEW'].includes(job.status)) return null;
  if (job.followUpDate) return job.followUpDate;
  if (job.status === 'APPLIED' && job.appliedDate) {
    const date = new Date(job.appliedDate + 'T12:00:00Z');
    date.setUTCDate(date.getUTCDate() + 8);
    return date.toISOString().slice(0, 10);
  }
  return null;
}
export function calendarFile(job: Job) {
  const escape = (s: string) =>
    s.replace(/\\/g, '\\\\').replace(/\r?\n/g, '\\n').replace(/,/g, '\\,').replace(/;/g, '\\;');
  const stamp = (d: Date) =>
    d
      .toISOString()
      .replace(/[-:]/g, '')
      .replace(/\.\d{3}/, '');
  const start = new Date(job.interviewDate!);
  const end = new Date(start.getTime() + 3600000);
  return [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//ApplyPilot//Application Calendar//EN',
    'BEGIN:VEVENT',
    `UID:${job.id}@applypilot`,
    `DTSTAMP:${stamp(new Date())}`,
    `DTSTART:${stamp(start)}`,
    `DTEND:${stamp(end)}`,
    `SUMMARY:${escape(job.title + ' — ' + job.company)}`,
    `LOCATION:${escape(job.location)}`,
    'END:VEVENT',
    'END:VCALENDAR',
    '',
  ].join('\r\n');
}
