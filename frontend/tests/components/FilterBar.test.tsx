import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { FilterBar } from "@/components/FilterBar";

function renderFilterBar(props: Partial<Parameters<typeof FilterBar>[0]> = {}) {
  const handlers = { onStatusChange: vi.fn(), onTypeChange: vi.fn() };
  render(
    <FilterBar selectedStatus={undefined} selectedType={undefined} {...handlers} {...props} />,
  );
  const [statusSelect, typeSelect] = screen.getAllByRole("combobox");
  return { ...handlers, statusSelect, typeSelect };
}

describe("FilterBar", () => {
  it("offers every status and incident type", () => {
    const { statusSelect, typeSelect } = renderFilterBar();

    expect(statusSelect.querySelectorAll("option")).toHaveLength(4);
    expect(typeSelect.querySelectorAll("option")).toHaveLength(7);
  });

  it("reports the selected status and type", async () => {
    const user = userEvent.setup();
    const { statusSelect, typeSelect, onStatusChange, onTypeChange } = renderFilterBar();

    await user.selectOptions(statusSelect, "IN_PROGRESS");
    await user.selectOptions(typeSelect, "FLOOD");

    expect(onStatusChange).toHaveBeenCalledWith("IN_PROGRESS");
    expect(onTypeChange).toHaveBeenCalledWith("FLOOD");
  });

  it("reports undefined when 'All' is selected", async () => {
    const user = userEvent.setup();
    const { statusSelect, onStatusChange } = renderFilterBar({ selectedStatus: "OPEN" });

    await user.selectOptions(statusSelect, "");

    expect(onStatusChange).toHaveBeenCalledWith(undefined);
  });

  it("hides 'Clear Filters' when no filter is active", () => {
    renderFilterBar();

    expect(screen.queryByRole("button", { name: "Clear Filters" })).not.toBeInTheDocument();
  });

  it("clears both filters", async () => {
    const user = userEvent.setup();
    const { onStatusChange, onTypeChange } = renderFilterBar({ selectedType: "FIRE" });

    await user.click(screen.getByRole("button", { name: "Clear Filters" }));

    expect(onStatusChange).toHaveBeenCalledWith(undefined);
    expect(onTypeChange).toHaveBeenCalledWith(undefined);
  });
});
