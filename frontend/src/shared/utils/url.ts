const URL_REGEX = /^(https?:\/\/)[\w.-]+(?:\.[\w\.-]+)+(?:[\w\-\._~:\/?#\[\]@!$&'()*+,;=%]*)$/i;

export function isValidUrl(candidate: string): boolean {
  if (!candidate) return false;
  try {
    const value = candidate.trim();
    if (!URL_REGEX.test(value)) {
      new URL(value);
    }
    return true;
  } catch {
    return false;
  }
}
