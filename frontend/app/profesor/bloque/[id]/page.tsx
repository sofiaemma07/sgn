"use client";

import { useEffect } from "react";
import { useParams, useRouter } from "next/navigation";

export default function BloquePage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();

  useEffect(() => {
    if (id) router.replace(`/profesor/bloque/${id}/planilla`);
  }, [id, router]);
  return null;
}
