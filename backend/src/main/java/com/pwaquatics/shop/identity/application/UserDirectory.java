package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.identity.domain.User;
import com.pwaquatics.shop.identity.domain.UserStatus;
import com.pwaquatics.shop.identity.infrastructure.UserRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Names of users for showing who did something (order history, notes) in the admin area. */
@Component
public class UserDirectory {

    private final UserRepository users;

    UserDirectory(UserRepository users) {
        this.users = users;
    }

    /**
     * @return name (or, without one, email address) per user id; deleted accounts read
     *     "Deleted account"; unknown ids are left out
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> displayNames(Collection<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        for (User user : users.findAllById(userIds)) {
            names.put(user.getId(), displayName(user));
        }
        return names;
    }

    private static String displayName(User user) {
        if (user.getStatus() == UserStatus.ANONYMISED) {
            return "Deleted account";
        }
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                        + (user.getLastName() == null ? "" : user.getLastName()))
                .strip();
        return name.isEmpty() ? user.getEmail() : name;
    }
}
