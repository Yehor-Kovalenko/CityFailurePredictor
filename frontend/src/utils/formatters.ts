import { formatDistanceToNow } from "date-fns";

export function formatTimestamp(timestamp: string | number | Date): string {
  return formatDistanceToNow(new Date(timestamp), { addSuffix: true });
}

export function formatDate(timestamp: string | number | Date): string {
  return new Date(timestamp).toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function shortenText(text: string | null | undefined, length: number): string {
  if (!text) return "";
  if (text.length <= length) return text;

  return text.slice(0, length - 3) + "...";
}