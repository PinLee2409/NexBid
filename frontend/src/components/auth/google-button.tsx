"use client";

import { useEffect, useRef, useState } from "react";

import { getGoogleClientId } from "@/services/session-service";

interface GoogleIdentity {
  accounts: {
    id: {
      initialize(options: { client_id: string; callback: (response: { credential: string }) => void }): void;
      renderButton(element: HTMLElement, options: Record<string, string | number>): void;
    };
  };
}

declare global {
  interface Window {
    google?: GoogleIdentity;
  }
}

const SCRIPT = "https://accounts.google.com/gsi/client";

function loadScript(): Promise<void> {
  if (window.google?.accounts) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT}"]`);
    const script = existing ?? Object.assign(document.createElement("script"), { src: SCRIPT, async: true });
    script.addEventListener("load", () => resolve(), { once: true });
    script.addEventListener("error", () => reject(new Error("Google sign-in did not load")), { once: true });
    if (!existing) document.head.appendChild(script);
  });
}

/**
 * EN: Google's own "Sign in with Google" button. Shown only when the server has a Google client id; the ID token it
 *     returns goes to onCredential. Renders nothing while loading or when Google cannot be reached.
 * VI: Nút "Đăng nhập bằng Google" của chính Google. Chỉ hiện khi server có client id Google; ID token nó trả về được
 *     đưa cho onCredential. Không hiện gì khi đang tải hoặc không kết nối được Google.
 */
export function GoogleButton({ onCredential, locale }: { onCredential: (credential: string) => void; locale: string }) {
  const holder = useRef<HTMLDivElement>(null);
  const callback = useRef(onCredential);
  const [clientId, setClientId] = useState<string | null>(null);

  useEffect(() => {
    callback.current = onCredential;
  }, [onCredential]);

  useEffect(() => {
    let live = true;
    void getGoogleClientId().then((id) => live && setClientId(id));
    return () => {
      live = false;
    };
  }, []);

  useEffect(() => {
    if (!clientId) return;
    let live = true;
    loadScript().then(
      () => {
        if (!live || !holder.current || !window.google) return;
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: (response) => callback.current(response.credential),
        });
        window.google.accounts.id.renderButton(holder.current, {
          theme: "outline",
          size: "large",
          text: "continue_with",
          width: holder.current.offsetWidth || 320,
          locale,
        });
      },
      () => undefined,
    );
    return () => {
      live = false;
    };
  }, [clientId, locale]);

  if (!clientId) return null;
  return <div ref={holder} className="flex min-h-11 w-full justify-center" data-testid="google-sign-in" />;
}
