const RELATIVE_FORMATTER = new Intl.RelativeTimeFormat(undefined, {
  numeric: 'auto'
});

export function formatRelativeTime(value: string | number | Date): string {
  const date = new Date(value);
  const now = Date.now();
  const diff = date.getTime() - now;

  const minutes = Math.round(diff / 60000);
  const hours = Math.round(diff / 3600000);
  const days = Math.round(diff / 86400000);

  if (Math.abs(minutes) < 60) {
    return RELATIVE_FORMATTER.format(minutes, 'minute');
  }

  if (Math.abs(hours) < 24) {
    return RELATIVE_FORMATTER.format(hours, 'hour');
  }

  return RELATIVE_FORMATTER.format(days, 'day');
}
