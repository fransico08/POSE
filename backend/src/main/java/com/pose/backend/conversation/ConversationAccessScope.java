package com.pose.backend.conversation;

import com.pose.backend.crm.CrmAccess;
import com.pose.backend.identity.ActorContext;
import com.pose.backend.identity.PermissionCode;
import com.pose.backend.identity.RoleCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shared SQL scope for conversation content and rows linked to conversations. */
@Component
public class ConversationAccessScope {
    private final CrmAccess crm;

    public ConversationAccessScope(CrmAccess crm) {
        this.crm = crm;
    }

    public Optional<Scope> readable(ActorContext actor, String conversationAlias, String channelAccountAlias) {
        if (actor.hasRole(RoleCode.DATA_STAFF) || !actor.hasPermission(PermissionCode.CONVERSATION_READ))
            return Optional.empty();
        List<String> predicates = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (crm.roleWith(actor, RoleCode.ADMIN, PermissionCode.CONVERSATION_READ)) predicates.add("1=1");
        if (crm.roleWith(actor, RoleCode.MANAGER, PermissionCode.CONVERSATION_READ)) {
            predicates.add("EXISTS (SELECT 1 FROM teams conversation_team WHERE conversation_team.organization_id="
                    + channelAccountAlias + ".organization_id AND conversation_team.id=" + channelAccountAlias
                    + ".team_id AND conversation_team.status='ACTIVE' AND conversation_team.manager_user_id=?)");
            args.add(actor.userId());
        }
        if (crm.roleWith(actor, RoleCode.SALES_REP, PermissionCode.CONVERSATION_READ)
                || crm.roleWith(actor, RoleCode.CUSTOMER_CARE, PermissionCode.CONVERSATION_READ)) {
            predicates.add(conversationAlias + ".assigned_user_id=?");
            args.add(actor.userId());
        }
        if (predicates.isEmpty()) return Optional.empty();
        return Optional.of(new Scope("(" + String.join(" OR ", predicates) + ")", List.copyOf(args)));
    }

    public record Scope(String where, List<Object> args) {}
}
