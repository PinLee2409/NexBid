"use client";

import { ImageOff } from "lucide-react";
import { useTranslations } from "next-intl";
import Image, { type ImageProps } from "next/image";
import { useState } from "react";

import { isLocalImage } from "@/lib/images";

type ProductPhotoProps = Omit<ImageProps, "src" | "alt" | "unoptimized" | "onError"> & {
  /** EN: Missing when the product has no photo yet. / VI: Không có khi sản phẩm chưa có ảnh. */
  src: string | undefined;
  alt?: string;
  /**
   * EN: What stands in for a missing photo: a caption, an icon for thumbnails, or nothing for backdrops.
   * VI: Thứ thay cho ảnh bị thiếu: dòng chữ, icon cho ảnh nhỏ, hoặc không gì cả cho ảnh nền.
   */
  fallback?: "label" | "icon" | "none";
};

/**
 * EN: A product photo filling its (positioned) frame. No photo, or a file the server no longer has, shows a
 *     quiet "no photo" mark instead of an empty box or a broken image.
 * VI: Ảnh sản phẩm lấp đầy khung (đã định vị) của nó. Không có ảnh, hoặc file server không còn, thì hiện dấu
 *     "chưa có ảnh" thay vì một ô trống hay ảnh vỡ.
 */
export function ProductPhoto({ src, alt = "", fallback = "label", className, ...image }: ProductPhotoProps) {
  const t = useTranslations("common");
  // EN: Remembered per URL, so a replaced photo gets its own chance. / VI: Nhớ theo URL, để ảnh thay mới vẫn được thử tải.
  const [failedSrc, setFailedSrc] = useState<string | null>(null);

  if (src && failedSrc !== src) {
    return (
      <Image
        {...image}
        fill
        src={src}
        alt={alt}
        unoptimized={isLocalImage(src)}
        onError={() => setFailedSrc(src)}
        className={className}
      />
    );
  }

  if (fallback === "none") return null;

  return (
    <span className="text-dim absolute inset-0 flex flex-col items-center justify-center gap-2">
      <ImageOff className={fallback === "icon" ? "size-4" : "size-6"} aria-hidden="true" />
      {fallback === "label" ? <span className="label-sm">{t("noPhoto")}</span> : null}
    </span>
  );
}
