import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import Home from "./page";

describe("Home", () => {
  it("hiển thị nền tảng frontend BookFlow", () => {
    render(<Home />);

    expect(
      screen.getByRole("heading", { level: 1, name: "BookFlow" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("status", { name: /Frontend foundation ready/i }),
    ).toBeInTheDocument();
  });
});
