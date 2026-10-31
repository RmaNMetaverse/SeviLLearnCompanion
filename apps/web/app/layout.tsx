import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = {title:"SeviL — Make every lecture make sense", description:"A multilingual study companion for international students."};
export default function Layout({ children }: { children: React.ReactNode }) {return <html lang="en"><body>{children}</body></html>;}
