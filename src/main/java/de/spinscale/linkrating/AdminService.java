package de.spinscale.linkrating;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AdminService implements Supplier<List<String>> {

    private final List<String> admins;

    public AdminService(String ... users) {
        if (users.length == 0) {
            final String[] admins = System.getenv("ADMINS").split(",");
            this.admins = Arrays.stream(admins).map(String::trim).collect(Collectors.toList());
        } else {
            this.admins = Arrays.asList(users);
        }
    }

    @Override
    public List<String> get() {
        return admins;
    }
}
