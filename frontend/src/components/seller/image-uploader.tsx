"use client";

import { ImagePlus, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useRef, useState, type DragEvent } from "react";

import { cn } from "@/lib/utils";

interface ImageUploaderProps {
  value: string[];
  onChange: (urls: string[]) => void;
  error?: string | null;
  id?: string;
}

const MAX_IMAGES = 6;

/**
 * Mock uploader. Files never leave the browser: each becomes an object URL
 * that the rest of the app renders unoptimised (`isLocalImage`). Swapping in a
 * real upload endpoint means replacing `filesToUrls` and nothing else.
 */
export function ImageUploader({ value, onChange, error, id }: ImageUploaderProps) {
  const t = useTranslations("seller");
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  // Object URLs leak until revoked; release the ones this component created.
  const createdRef = useRef<string[]>([]);
  useEffect(() => {
    const created = createdRef.current;
    return () => {
      for (const url of created) URL.revokeObjectURL(url);
    };
  }, []);

  function addFiles(files: FileList | null) {
    if (!files || files.length === 0) return;

    const urls = Array.from(files)
      .filter((file) => file.type.startsWith("image/"))
      .slice(0, MAX_IMAGES - value.length)
      .map((file) => {
        const url = URL.createObjectURL(file);
        createdRef.current.push(url);
        return url;
      });

    if (urls.length > 0) onChange([...value, ...urls]);
  }

  function removeAt(index: number) {
    onChange(value.filter((_, position) => position !== index));
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setDragging(false);
    addFiles(event.dataTransfer.files);
  }

  return (
    <div className="space-y-3">
      <div
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        className={cn(
          "border transition-colors",
          dragging ? "border-signal-text bg-signal-dim" : "border-line bg-surface",
          error && "border-danger-text",
        )}
      >
        <button
          type="button"
          id={id}
          onClick={() => inputRef.current?.click()}
          disabled={value.length >= MAX_IMAGES}
          className="focus-visible:ring-ring flex w-full flex-col items-center gap-2 px-6 py-10 focus-visible:ring-2 focus-visible:outline-none disabled:opacity-50"
        >
          <span className="text-dim flex size-10 items-center justify-center">
            <ImagePlus className="size-5" aria-hidden="true" />
          </span>
          <span className="label">{t("addPhoto")}</span>
          <span className="text-muted-foreground text-xs">
            {t("addPhotoHint")}
          </span>
        </button>

        <input
          ref={inputRef}
          type="file"
          accept="image/*"
          multiple
          className="sr-only"
          onChange={(event) => {
            addFiles(event.target.files);
            event.target.value = "";
          }}
        />
      </div>

      {value.length > 0 ? (
        <ul className="grid grid-cols-3 gap-3 sm:grid-cols-4">
          {value.map((url, index) => (
            <li key={url} className="group relative">
              <div className="on-media bg-surface relative aspect-square overflow-hidden">
                {/* Plain img: these are local object URLs, not remote assets. */}
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={url}
                  alt=""
                  className="size-full object-cover"
                />
              </div>

              {index === 0 ? (
                <span className="bg-signal text-signal-ink label-sm absolute top-2 left-2 px-1.5 py-0.5">
                  {t("coverPhoto")}
                </span>
              ) : null}

              <button
                type="button"
                onClick={() => removeAt(index)}
                aria-label={t("removePhoto")}
                className="on-media text-foreground hover:text-danger-text absolute top-2 right-2 inline-flex size-7 items-center justify-center transition-colors"
              >
                <X className="size-3" />
              </button>
            </li>
          ))}
        </ul>
      ) : null}

      <p className="text-muted-foreground text-xs">{t("productImagesHint")}</p>
    </div>
  );
}
