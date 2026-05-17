/**
 * Small style helpers shared by shell chrome components.
 */

export function withAlpha(color: string, alpha: number): string {
  const clampedAlpha = Math.max(0, Math.min(1, alpha));
  const trimmed = color.trim();
  const hex = trimmed.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i);

  if (hex) {
    const raw = hex[1];
    const normalized = raw.length === 3
      ? raw.split('').map((char) => `${char}${char}`).join('')
      : raw;
    const red = Number.parseInt(normalized.slice(0, 2), 16);
    const green = Number.parseInt(normalized.slice(2, 4), 16);
    const blue = Number.parseInt(normalized.slice(4, 6), 16);
    return `rgba(${red}, ${green}, ${blue}, ${clampedAlpha})`;
  }

  const rgb = trimmed.match(/^rgba?\(([^)]+)\)$/i);
  if (rgb) {
    const channels = rgb[1].split(',').slice(0, 3).map((part) => part.trim());
    if (channels.length === 3) {
      return `rgba(${channels[0]}, ${channels[1]}, ${channels[2]}, ${clampedAlpha})`;
    }
  }

  return `color-mix(in srgb, ${trimmed} ${Math.round(clampedAlpha * 100)}%, transparent)`;
}