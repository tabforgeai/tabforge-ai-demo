package security;

import java.util.HashSet;
import java.util.Set;

import jakarta.faces.context.FacesContext;
import jakarta.interceptor.Interceptor;

/**
 * Demo interceptor that resolves the current user's roles from the
 * <b>Jakarta Security</b> caller identity instead of a hand-maintained session attribute.
 *
 * <h2>Why this class exists</h2>
 * <p>The library's {@link InMemoryAccessCheckInterceptor} reads roles from the
 * {@code "user_roles"} session attribute by default. That default is convenient for a
 * library that cannot assume any particular security stack, but in a real Jakarta EE
 * application the container already knows who the caller is and which roles they hold —
 * it established that identity when {@code CustomInMemoryIdentityStore} validated the
 * login. Copying those roles into a session attribute duplicates the source of truth and
 * can drift. This subclass overrides only {@link #getUserRoles()} to ask the platform
 * directly ({@code ExternalContext.isUserInRole}), and inherits every access rule
 * (declared {@code allowedRoles}, programmatic {@code grantAccess}, fail-closed default)
 * from the parent unchanged.</p>
 *
 * <h2>Familiar analogy</h2>
 * <p>The default is like keeping a photocopy of someone's ID badge in a drawer and
 * checking the copy. This class instead scans the live badge at the door — there is one
 * authority (the building's access system, i.e. the container), and no stale copy to keep
 * in sync.</p>
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   login → CustomInMemoryIdentityStore.validate() → container records caller + roles
 *        → secured @DynTab / @AccessCheck method invoked
 *        → this.getUserRoles() asks ExternalContext.isUserInRole(...) for the live roles
 *        → inherited hasPermission() decides
 * </pre>
 *
 * <p><b>Wiring:</b> enabled in {@code WEB-INF/beans.xml} in place of the library class:</p>
 * <pre>{@code
 * <interceptors>
 *     <class>security.SecurityContextAccessCheckInterceptor</class>
 * </interceptors>
 * }</pre>
 *
 * @author svetozar.radojcin
 * @see InMemoryAccessCheckInterceptor
 */
@Interceptor
@AccessCheck
public class SecurityContextAccessCheckInterceptor extends InMemoryAccessCheckInterceptor {

    private static final long serialVersionUID = 1L;

    /**
     * The role universe this demo declares, mirroring the {@code <security-role>}
     * entries in {@code web.xml}. {@code isUserInRole} answers per-role, so we test the
     * known roles and collect the ones the caller holds.
     */
    private static final Set<String> KNOWN_ROLES = Set.of("ADMIN", "USER");

    /**
     * Returns the caller's roles as seen by the container, not by a session attribute.
     *
     * <p>{@code ExternalContext.isUserInRole(role)} reflects the identity Jakarta Security
     * established at login, so there is nothing to populate or keep in sync. Outside a JSF
     * request (no {@code FacesContext}) we return an empty set, which — combined with the
     * fail-closed default — denies rather than accidentally grants.</p>
     *
     * @return the subset of {@link #KNOWN_ROLES} the current caller holds
     */
    @Override
    protected Set<String> getUserRoles() {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (facesContext == null) {
            return Set.of();
        }
        Set<String> roles = new HashSet<>();
        for (String role : KNOWN_ROLES) {
            if (facesContext.getExternalContext().isUserInRole(role)) {
                roles.add(role);
            }
        }
        return roles;
    }
}
