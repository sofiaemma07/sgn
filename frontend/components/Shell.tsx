"use client";
import type { ReactNode } from "react";
import Navbar from "./Navbar";
export default function Shell({children, className = ""}: {children:ReactNode; className?: string}) {
  return <div className="shell"><Navbar/><main className={`main page ${className}`.trim()}>{children}</main></div>;
}
