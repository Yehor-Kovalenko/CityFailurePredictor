import { useEffect } from "react";
import { hideSplash } from "../splash";

export function useHideSplash(ready: boolean): void {
  useEffect(() => {
    if (ready) {
      void hideSplash();
    }
  }, [ready]);
}
