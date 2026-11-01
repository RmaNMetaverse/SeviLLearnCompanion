import type { Metadata, Viewport } from "next";
import "./globals.css";
export const metadata: Metadata = {
  manifest: "/manifest.webmanifest",
  appleWebApp: {capable:true,title:"SeviL",statusBarStyle:"default"},
  icons: {icon:"/icon-192.png",apple:"/apple-touch-icon.png"},
  title: "SeviL — Make every lecture make sense",
  description: "A multilingual study companion for international students.",
};
export const viewport: Viewport = {width:"device-width",initialScale:1,viewportFit:"cover",themeColor:"#246D5E"};
export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
