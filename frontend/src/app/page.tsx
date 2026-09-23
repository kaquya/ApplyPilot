'use client';

import dynamic from 'next/dynamic';

// Dates and the saved interface language belong to the current browser session.
const Workspace = dynamic(() => import('@/components/workspace'), {
  ssr: false,
  loading: () => (
    <div className="workspace-loading" role="status">
      ApplyPilot<span>Loading your workspace…</span>
    </div>
  ),
});

export default function Page() {
  return <Workspace />;
}
