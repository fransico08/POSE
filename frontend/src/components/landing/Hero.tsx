import Image from "next/image";
import productImage from "../../../public/images/pose-crm-overview.webp";
import { commonCopy, heroCopy, landingMeta, productCaption, scopeNote } from "@/content/landing";

export function Hero() {
  return <section className="landing-hero" aria-labelledby="hero-title">
    <div className="landing-container landing-hero-layout">
      <div className="landing-hero-copy">
        <p className="eyebrow">{heroCopy.eyebrow}</p>
        <h1 id="hero-title">{heroCopy.title}</h1>
        <p>{heroCopy.introduction}</p><p>{heroCopy.description}</p>
        <a className="button button-primary" href="/dang-nhap">{commonCopy.login}</a>
        <p className="landing-account-note">{commonCopy.accountNote}</p>
        <p className="landing-scope-note">{scopeNote}</p>
      </div>
      <figure className="landing-product">
        <Image src={productImage} alt={landingMeta.imageAlt} preload sizes="(min-width: 1024px) 55vw, 100vw" />
        <figcaption>{productCaption}</figcaption>
      </figure>
    </div>
  </section>;
}
