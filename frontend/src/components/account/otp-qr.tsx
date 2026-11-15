"use client";

import { toDataURL } from "qrcode";
import { useEffect, useState } from "react";

/**
 * EN: The otpauth link as a QR code for the authenticator app to scan, drawn in the browser so the secret never leaves
 *     it. Dark modules on white, as every scanner expects, whatever the theme.
 * VI: Link otpauth dạng mã QR để ứng dụng xác thực quét, vẽ ngay trong trình duyệt nên secret không đi đâu cả. Ô đen trên
 *     nền trắng như mọi máy quét mong đợi, dù đang ở giao diện nào.
 */
export function OtpQr({ uri, label }: { uri: string; label: string }) {
  const [image, setImage] = useState<{ uri: string; src: string } | null>(null);

  useEffect(() => {
    let live = true;
    toDataURL(uri, { errorCorrectionLevel: "M", margin: 2, width: 200, color: { dark: "#000000", light: "#ffffff" } })
      .then((src) => live && setImage({ uri, src }))
      .catch(() => live && setImage(null));
    return () => {
      live = false;
    };
  }, [uri]);

  if (!image || image.uri !== uri) return <div className="bg-muted size-[200px]" aria-hidden="true" />;
  // eslint-disable-next-line @next/next/no-img-element -- a data URL made here; next/image adds nothing
  return <img src={image.src} alt={label} width={200} height={200} className="block" data-testid="two-factor-qr" />;
}
