import type { MetadataRoute } from "next";
import { contentUpdatedAt } from "@/content/landing";
import { getSiteUrl } from "@/lib/site";

export default function sitemap(): MetadataRoute.Sitemap {
  return [{ url: getSiteUrl().href, lastModified: contentUpdatedAt }];
}
