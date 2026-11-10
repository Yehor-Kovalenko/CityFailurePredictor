import { Incident } from "@/types";

export type ShareOutcome = "shared" | "copied" | "cancelled";

export async function shareIncident(incident: Incident): Promise<ShareOutcome> {
  const { x: latitude, y: longitude } = incident.coordinates;
  const url = `https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=17/${latitude}/${longitude}`;
  const text = [
    `${incident.incidentType} incident, status ${incident.status}`,
    incident.incidentSummary,
    `Location: ${latitude}, ${longitude}`,
  ]
    .filter(Boolean)
    .join("\n");
  const data: ShareData = { title: incident.incidentTitle, text, url };

  if (navigator.share && (!navigator.canShare || navigator.canShare(data))) {
    try {
      await navigator.share(data);
      return "shared";
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        return "cancelled";
      }
      throw error;
    }
  }

  await navigator.clipboard.writeText(`${incident.incidentTitle}\n${text}\n${url}`);
  return "copied";
}
