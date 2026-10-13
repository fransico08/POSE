import { roles, sectionCopy } from "@/content/landing";
import { SectionHeading } from "./SectionHeading";
import { RoleTabs } from "./RoleTabs";
import { RoleMock } from "./mock/RoleMock";
import { CheckCircleIcon } from "@heroicons/react/20/solid";
export function RoleSection() {
  const panels = roles.map(role => <div className="role-layout" key={role.id}><RoleMock role={role.id} /><div className="role-copy"><h3 data-marketing>{role.title}</h3><ul>{role.responsibilities.map(text => <li key={text}><CheckCircleIcon aria-hidden="true" /><span data-marketing>{text}</span></li>)}</ul><p className="demo-label">Giao diện minh họa</p></div></div>);
  return <section id="vai-tro" className="landing-section landing-canvas" aria-labelledby="roles-title"><div className="landing-container"><SectionHeading id="roles-title" {...sectionCopy.roles} /><RoleTabs labels={roles.map(role => role.label)} panels={panels} /></div></section>;
}
