import Link from 'next/link';

interface EmptyStateProps {
  eyebrow: string;
  title: string;
  description: string;
  actionHref: string;
  actionLabel: string;
  tone?: 'default' | 'map' | 'quiet';
}

export function EmptyState({
  eyebrow,
  title,
  description,
  actionHref,
  actionLabel,
  tone = 'default',
}: EmptyStateProps) {
  return (
    <section className="empty-state" data-tone={tone} aria-label={title}>
      <div className="empty-state-mark" aria-hidden="true">
        <span />
        <span />
      </div>
      <p className="overline">{eyebrow}</p>
      <h2>{title}</h2>
      <p>{description}</p>
      <Link className="empty-state-action" href={actionHref}>
        {actionLabel} <span aria-hidden="true">→</span>
      </Link>
    </section>
  );
}
