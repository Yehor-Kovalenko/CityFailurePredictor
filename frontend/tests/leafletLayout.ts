import {vi} from "vitest";

export function stubLeafletLayout() {
    vi.spyOn(HTMLElement.prototype, "offsetWidth", "get").mockReturnValue(1);
    vi.spyOn(HTMLElement.prototype, "offsetHeight", "get").mockReturnValue(1);
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockReturnValue(
        {left: 0, top: 0, right: 1, bottom: 1, width: 1, height: 1, x: 0, y: 0, toJSON: () => ({})},
    );
}
