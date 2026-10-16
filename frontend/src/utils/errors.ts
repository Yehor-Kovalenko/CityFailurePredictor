import { isAxiosError } from "axios";
import { ErrorResponse } from "../types";

/** Returns the backend error message from an API error, or the fallback for any other failure. */
export function getErrorMessage(error: unknown, fallback: string): string {
  if (isAxiosError<Partial<ErrorResponse>>(error)) {
    return error.response?.data?.message || fallback;
  }
  return fallback;
}
