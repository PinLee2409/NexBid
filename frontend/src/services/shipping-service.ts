"use client";

import { useEffect, useState } from "react";

import { api } from "./api/http";

/**
 * EN: Shipping with GHN: whether it is connected, GHN's province / district / ward lists for addresses, the fee guide on
 *     a lot page, and the carriers a seller picks from. Everything here works without GHN: the lists and the guide are
 *     simply not shown.
 * VI: Giao hàng với GHN: đã kết nối chưa, danh sách tỉnh / quận / phường của GHN cho địa chỉ, phí ship tham khảo trên
 *     trang lô, và các hãng người bán chọn. Mọi thứ ở đây chạy được khi không có GHN: danh sách và phí chỉ không hiện.
 */

export interface Area {
  id: string;
  name: string;
}

type EstimateState = "OFF" | "NEEDS_ADDRESS" | "NO_ORIGIN" | "UNAVAILABLE" | "READY";

export interface ShippingEstimate {
  state: EstimateState;
  /** EN: VND. / VI: VND. */
  fee: number | null;
  district: string | null;
  province: string | null;
  /** EN: The seller gave no packed size; quoted for 1 kg in a small box. / VI: Người bán chưa khai kích thước; báo theo 1 kg hộp nhỏ. */
  defaultParcel: boolean;
}

let ghnStatus: Promise<boolean> | null = null;

/** `GET /api/shipping/status` — asked once per page load. */
function ghnEnabled(): Promise<boolean> {
  ghnStatus ??= api<{ ghn: boolean }>("/api/shipping/status")
    .then((status) => status.ghn)
    .catch(() => {
      ghnStatus = null;
      return false;
    });
  return ghnStatus;
}

/** EN: False until known, then whether GHN is connected. / VI: False cho tới khi biết, rồi là GHN đã kết nối chưa. */
export function useGhnEnabled(): boolean {
  const [enabled, setEnabled] = useState(false);
  useEffect(() => {
    let live = true;
    void ghnEnabled().then((value) => {
      if (live) setEnabled(value);
    });
    return () => {
      live = false;
    };
  }, []);
  return enabled;
}

/** `GET /api/shipping/ghn/provinces` */
export function listProvinces(): Promise<Area[]> {
  return api<Area[]>("/api/shipping/ghn/provinces");
}

/** `GET /api/shipping/ghn/districts?provinceId=` */
export function listDistricts(provinceId: number): Promise<Area[]> {
  return api<Area[]>(`/api/shipping/ghn/districts?provinceId=${provinceId}`);
}

/** `GET /api/shipping/ghn/wards?districtId=` */
export function listWards(districtId: number): Promise<Area[]> {
  return api<Area[]>(`/api/shipping/ghn/wards?districtId=${districtId}`);
}

/** `GET /api/auctions/{id}/shipping-estimate` — signed out is fine. */
export async function getShippingEstimate(auctionId: string): Promise<ShippingEstimate> {
  const estimate = await api<Omit<ShippingEstimate, "fee"> & { fee: number | string | null }>(
    `/api/auctions/${auctionId}/shipping-estimate`,
  );
  return { ...estimate, fee: estimate.fee === null ? null : Number(estimate.fee) };
}
