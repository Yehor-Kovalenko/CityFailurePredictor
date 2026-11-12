import { useEffect, useState } from "react";
import { ImageOff, X } from "lucide-react";
import { Incident } from "../types";
import { incidentService } from "../services/api";

interface IncidentPhotoProps {
  incident: Incident;
}

export function IncidentPhoto({ incident }: IncidentPhotoProps) {
  const [remoteUrl, setRemoteUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const [isExpanded, setIsExpanded] = useState(false);
  const shouldFetch = !incident.photo && Boolean(incident.hasPhoto);

  useEffect(() => {
    if (!shouldFetch) {
      return;
    }

    let objectUrl: string | null = null;
    let cancelled = false;
    setFailed(false);
    incidentService
      .getIncidentPhoto(incident.id)
      .then((blob) => {
        if (!cancelled) {
          objectUrl = URL.createObjectURL(blob);
          setRemoteUrl(objectUrl);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setFailed(true);
        }
      });

    return () => {
      cancelled = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [incident.id, shouldFetch]);

  const src = incident.photo ?? remoteUrl;
  const alt = `Photo of ${incident.incidentTitle}`;

  if (!incident.photo && !incident.hasPhoto) {
    return null;
  }

  if (failed) {
    return (
      <div className="mb-3 flex h-40 items-center justify-center gap-2 rounded-lg bg-dark-900 text-sm text-gray-500">
        <ImageOff size={18} aria-hidden="true" />
        Photo unavailable offline
      </div>
    );
  }

  if (!src) {
    return <div className="mb-3 h-40 animate-pulse rounded-lg bg-dark-900" aria-hidden="true" />;
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setIsExpanded(true)}
        className="mb-3 block w-full overflow-hidden rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
        aria-label={`Open ${alt.toLowerCase()}`}
      >
        <img src={src} alt={alt} className="h-40 w-full object-cover" decoding="async" />
      </button>

      {isExpanded && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label={alt}
          onClick={() => setIsExpanded(false)}
          onKeyDown={(event) => event.key === "Escape" && setIsExpanded(false)}
          className="fixed inset-0 z-[100000] overflow-auto overscroll-contain bg-black/90 p-4 pt-safe pb-safe"
        >
          <button
            type="button"
            autoFocus
            onClick={() => setIsExpanded(false)}
            aria-label="Close photo"
            className="fixed right-4 top-4 z-10 rounded-full bg-black/60 p-2 text-white hover:bg-black/80"
          >
            <X size={24} />
          </button>
          <img src={src} alt={alt} className="mx-auto max-w-none" onClick={(event) => event.stopPropagation()} />
        </div>
      )}
    </>
  );
}
