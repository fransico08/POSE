import { commonCopy, ctaCopy } from "@/content/landing";

export function CtaBand() {
  return <section className="landing-cta landing-section" aria-labelledby="cta-title"><div className="landing-container"><h2 id="cta-title">{ctaCopy.title}</h2><p>{ctaCopy.description}</p><a className="button button-primary" href="/dang-nhap">{commonCopy.login}</a></div></section>;
}
