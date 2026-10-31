import Image from "next/image";
import education from "@/assets/landing/frames/education.jpg";
import property from "@/assets/landing/frames/property.jpg";
import software from "@/assets/landing/frames/software.jpg";
import logistics from "@/assets/landing/frames/logistics.jpg";
import clinic from "@/assets/landing/frames/clinic.jpg";
import { heroFrames } from "@/content/landing";
import { HeroMotion } from "./HeroMotion";
const photos = { education, property, software, logistics, clinic };
function RetailArt() {
  return <svg className="coverflow-retail-art" viewBox="0 0 240 320" aria-hidden="true" focusable="false">
    <defs><linearGradient id="retail-warm" x2="1" y2="1"><stop stopColor="#1E293B" /><stop offset="1" stopColor="#78350F" /></linearGradient></defs>
    <rect width="240" height="320" fill="url(#retail-warm)" />
    <g stroke="#F59E0B" strokeWidth="2" fill="none"><path d="M32 88h176v160H32zM32 116h176M56 248v22m128-22v22M78 44v22m84-22v22" /><path d="M64 152h72v48H64zm72 10h12a13 13 0 010 26h-12M54 210h112M150 130h34v80h-34z" /></g>
    <g fill="#FDE68A"><circle cx="78" cy="72" r="12" /><circle cx="162" cy="72" r="12" /><path d="M48 92h144v7H48z" /></g>
  </svg>;
}
export function HeroFrames() {
  const images = heroFrames.map((frame, i) => {
    const photo = photos[frame.id as keyof typeof photos];
    return photo ? <Image key={frame.id} src={photo} alt="" width={1200} height={1600} sizes="(max-width: 767px) 96px, (max-width: 1279px) 148px, 176px" loading={i === 0 ? "eager" : "lazy"} fetchPriority="low" /> : <RetailArt key={frame.id} />;
  });
  // Reuse each static import's tiny thumbnail for blur: no second full-image request.
  const backdrops = heroFrames.map(frame => {
    const photo = photos[frame.id as keyof typeof photos];
    return <div key={frame.id} className="hero-backdrop-image" style={photo ? { backgroundImage: `url("${photo.blurDataURL}")` } : undefined} />;
  });
  return <HeroMotion frames={heroFrames} images={images} backdrops={backdrops} />;
}
