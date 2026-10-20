import React from "react";
import ReactDOM from "react-dom/client";
import { Analytics } from "@vercel/analytics/react";
import { App } from "./app/App";
import "./styles.css";

if (new URLSearchParams(window.location.search).get("embedded") === "1") {
  document.documentElement.classList.add("embedded-editor");
}

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <>
      <App />
      <Analytics />
    </>
  </React.StrictMode>
);
