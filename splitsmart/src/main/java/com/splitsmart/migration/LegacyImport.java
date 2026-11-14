package com.splitsmart.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitsmart.model.Expense;
import com.splitsmart.model.GroupEntity;
import com.splitsmart.model.GroupState;
import com.splitsmart.model.PaymentEntry;
import com.splitsmart.model.SystemState;
import com.splitsmart.repository.GroupRepository;
import com.splitsmart.repository.SystemStateRepository;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** One-time, transactional import. Original files are preserved and never modified. */
@Component
public class LegacyImport implements ApplicationRunner {
    private final GroupRepository groups;
    private final SystemStateRepository system;
    private final ObjectMapper json;
    private final Path root;

    public LegacyImport(
            GroupRepository groups,
            SystemStateRepository system,
            ObjectMapper json,
            @Value("${splitsmart.data-directory:data}") String directory) {
        this.groups = groups;
        this.system = system;
        this.json = json;
        this.root = Path.of(directory);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        if (system.existsById("legacy-import-v1")) return;
        if (Files.isRegularFile(root.resolve("state.bin")))
            importGroup("default", "Original group", root);
        Path folder = root.resolve("groups");
        if (Files.isDirectory(folder))
            try (var directories = Files.list(folder)) {
                for (Path dir : directories.sorted().toList())
                    if (dir.getFileName().toString().matches("[a-f0-9-]{36}")
                            && Files.isRegularFile(dir.resolve("name.txt")))
                        importGroup(
                                dir.getFileName().toString(),
                                Files.readString(dir.resolve("name.txt")),
                                dir);
            }
        system.save(new SystemState("legacy-import-v1"));
    }

    private void importGroup(String id, String name, Path folder) throws Exception {
        if (groups.existsById(id)) return;
        GroupState state = new GroupState();
        Path file = folder.resolve("state.bin");
        if (Files.exists(file))
            try (var in = new DataInputStream(Files.newInputStream(file))) {
                int n = in.readInt();
                boolean v2 = n == -2;
                if (v2) n = in.readInt();
                if (n < 0 || n > 30) throw new IOException("Invalid legacy member count");
                for (int i = 0; i < n; i++) state.members.add(in.readUTF());
                n = in.readInt();
                if (n < 0 || n > 10000) throw new IOException("Invalid legacy expense count");
                for (int i = 0; i < n; i++) {
                    String eid = in.readUTF(), title = in.readUTF();
                    long cents = in.readLong();
                    String payer = in.readUTF();
                    int k = in.readInt();
                    if (k <= 0 || k > 30) throw new IOException("Invalid legacy shares");
                    List<String> members = new ArrayList<>();
                    for (int j = 0; j < k; j++) members.add(in.readUTF());
                    if (v2) {
                        String mode = in.readUTF();
                        List<Long> shares = new ArrayList<>();
                        for (int j = 0; j < k; j++) shares.add(in.readLong());
                        state.expenses.add(
                                new Expense(eid, title, cents, payer, members, shares, mode));
                    } else state.expenses.add(new Expense(eid, title, cents, payer, members));
                }
            }
        Path paymentFile = folder.resolve("payments.json");
        if (Files.exists(paymentFile))
            state.payments =
                    json.readValue(
                            paymentFile.toFile(), new TypeReference<List<PaymentEntry>>() {});
        groups.save(new GroupEntity(id, name, null, json.writeValueAsString(state)));
    }
}
