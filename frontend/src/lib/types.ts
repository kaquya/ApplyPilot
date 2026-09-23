export type Language = 'en' | 'de' | 'fr' | 'pl';
export type Status = 'INTERESTED' | 'APPLIED' | 'INTERVIEW' | 'OFFER' | 'REJECTED';
export const statuses: Status[] = ['INTERESTED', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED'];
export type Analysis = {
  summary: string;
  requirements: {
    requirement: string;
    status: 'matched' | 'partial' | 'missing';
    evidence: string;
    suggestion: string;
  }[];
  topics: string[];
  questions: string[];
  weaknesses: string[];
  letter: string;
  changes: string[];
};
export type JobData = {
  title: string;
  company: string;
  status: Status;
  location: string;
  canton: string;
  url: string;
  source: string;
  description: string;
  salaryMin: number | null;
  salaryMax: number | null;
  workload: number | null;
  contactName: string;
  contactEmail: string;
  appliedDate: string | null;
  interviewDate: string | null;
  followUpDate: string | null;
  notes: string;
  profileId: string | null;
  language: Language;
};
export type Job = JobData & {
  id: string;
  version: number;
  createdAt: string;
  analysis?: Analysis;
  coverLetter?: Analysis;
  interviewPrep?: Analysis;
  tailoring?: Analysis;
};
export type Profile = {
  id: string;
  version: number;
  name: string;
  language: Language;
  text: string;
  documentId: string | null;
};
export type Document = {
  id: string;
  entryId: string;
  filename: string;
  size: number;
  createdAt: string;
  text: string;
};
export type Account = {
  id: string;
  name: string;
  email: string;
  plan: string;
  emailVerified: boolean;
  emailReminders: boolean;
};
export type Config = {
  ai: boolean;
  google: boolean;
  billing: boolean;
  registration: boolean;
  email: boolean;
};
export function jobData(job: Job): JobData {
  const {
    title,
    company,
    status,
    location,
    canton,
    url,
    source,
    description,
    salaryMin,
    salaryMax,
    workload,
    contactName,
    contactEmail,
    appliedDate,
    interviewDate,
    followUpDate,
    notes,
    profileId,
    language,
  } = job;
  return {
    title,
    company,
    status,
    location,
    canton,
    url,
    source,
    description,
    salaryMin,
    salaryMax,
    workload,
    contactName,
    contactEmail,
    appliedDate,
    interviewDate,
    followUpDate,
    notes,
    profileId,
    language,
  };
}
export function emptyJob(): JobData {
  return {
    title: '',
    company: '',
    status: 'INTERESTED',
    location: '',
    canton: '',
    url: '',
    source: 'Company website',
    description: '',
    salaryMin: null,
    salaryMax: null,
    workload: 100,
    contactName: '',
    contactEmail: '',
    appliedDate: null,
    interviewDate: null,
    followUpDate: null,
    notes: '',
    profileId: null,
    language: 'en',
  };
}
