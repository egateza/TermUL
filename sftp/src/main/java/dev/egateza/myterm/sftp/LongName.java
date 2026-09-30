package dev.egateza.myterm.sftp;

/** Parser "longname" SFTP v3 (format {@code ls -l}) untuk mengambil nama owner dan group. */
final class LongName {

    private LongName() {
    }

    /** @return {owner, group}, atau null kalau format tidak dikenali */
    static String[] ownerGroup(String longName) {
        if (longName == null || longName.isBlank()) {
            return null;
        }
        String[] parts = longName.strip().split("\\s+");
        // perms links owner group size ...
        if (parts.length < 5 || parts[0].length() < 10 || !parts[1].chars().allMatch(Character::isDigit)) {
            return null;
        }
        return new String[] {parts[2], parts[3]};
    }
}
