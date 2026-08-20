package com.leosprojects.busdisplay.sound;

public final class SoundPackProfile {
    public final String id;
    public final String name;
    public final String description;
    public final String folder;
    public final boolean qa;

    public SoundPackProfile(String id, String name, String description,
                            String folder, boolean qa) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.folder = folder;
        this.qa = qa;
    }

    @Override
    public String toString() { return name; }
}
