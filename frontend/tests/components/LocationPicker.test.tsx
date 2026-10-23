import {fireEvent, render} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {LocationPicker} from "@/components/LocationPicker";
import {stubLeafletLayout} from "../leafletLayout";

describe("LocationPicker", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("shows no pin until a location is chosen", () => {
        const {container} = render(<LocationPicker value={null} onChange={vi.fn()}/>);

        expect(container.querySelector(".location-picker-pin")).not.toBeInTheDocument();
    });

    it("shows the pin and reports a clicked point", () => {
        stubLeafletLayout();
        const onChange = vi.fn();
        const {container} = render(
            <LocationPicker value={{lat: 51.75, lng: 19.45}} onChange={onChange}/>,
        );

        expect(container.querySelector(".location-picker-pin")).toBeInTheDocument();

        fireEvent.click(container.querySelector(".leaflet-container")!, {clientX: 0, clientY: 0});

        const [point] = onChange.mock.calls[0];
        expect(point.lat).toBeCloseTo(51.75, 4);
        expect(point.lng).toBeCloseTo(19.45, 4);
    });
});
