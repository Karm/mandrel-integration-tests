package org.acme;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

@Path("/")
public class ExampleResource {

    @GET
    @Path("/ready")
    @Produces(MediaType.TEXT_PLAIN)
    public Response ready() {
        return Response.ok().entity("Yes.").build();
    }

    @GET
    @Path("/dump")
    @Produces(MediaType.APPLICATION_JSON)
    public Response hello() {
        final Map<Integer, Record> m = new HashMap<>();
        for (int i = 0; i < 10_000; i++) {
            final Record r = new Record();
            r.s0 = "A".repeat(100);
            r.s1 = "B".repeat(200);
            r.s2 = "C".repeat(300);
            r.s3 = "D".repeat(10);
            r.s4 = "E".repeat(20);
            r.s5 = "F".repeat(30);
            r.s6 = "G".repeat(40);
            r.s7 = "H".repeat(50);
            r.s8 = "I".repeat(60);
            r.s9 = "J".repeat(60);
            r.i0 = Integer.MAX_VALUE;
            r.i1 = Integer.MIN_VALUE;
            r.i2 = 0;
            r.i3 = 10;
            r.i4 = 20;
            r.i5 = 30;
            r.i6 = 40;
            r.l0 = Stream.of(lorem).collect(Collectors.toList());
            r.m0 = Stream.of(lorem).collect(Collectors.toMap(String::toLowerCase, String::toUpperCase, (x, y) -> y));
            m.put(i, r);
            r.bi0 = new BigInteger("1".repeat(128));
        }
        return Response.ok().entity(m).build();
    }

    @POST
    @Path("/load")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response load(Map<Integer, Record> m) {
        return Response.ok().entity("Hashcode: " + m.hashCode() + "\nSize: " + m.size()).build();
    }

    @POST
    @Path("/load/hash")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response loadHash(Map<Integer, Record> m) throws NoSuchAlgorithmException {
        final ByteArrayOutputStream ba = new ByteArrayOutputStream(100_000_000);
        m.forEach((k, v) -> {
                    ba.writeBytes(k.toString().getBytes(UTF_8));
                    ba.writeBytes(v.s0.getBytes(UTF_8));
                    ba.writeBytes(v.s1.getBytes(UTF_8));
                    ba.writeBytes(v.s2.getBytes(UTF_8));
                    ba.writeBytes(v.s3.getBytes(UTF_8));
                    ba.writeBytes(v.s4.getBytes(UTF_8));
                    ba.writeBytes(v.s5.getBytes(UTF_8));
                    ba.writeBytes(v.s6.getBytes(UTF_8));
                    ba.writeBytes(v.s7.getBytes(UTF_8));
                    ba.writeBytes(v.s8.getBytes(UTF_8));
                    ba.writeBytes(v.s9.getBytes(UTF_8));
                    ba.writeBytes(Integer.toString(v.i0).getBytes(UTF_8));
                    ba.writeBytes(Integer.toString(v.i1).getBytes(UTF_8));
                    ba.writeBytes(Integer.toString(v.i2).getBytes(UTF_8));
                    ba.writeBytes(Integer.toString(v.i3).getBytes(UTF_8));
                    ba.writeBytes(Integer.toString(v.i4).getBytes(UTF_8));
                    ba.writeBytes(Integer.toString(v.i5).getBytes(UTF_8));
                    ba.writeBytes(Integer.toString(v.i6).getBytes(UTF_8));
                    v.l0.forEach(s -> ba.writeBytes(s.getBytes(UTF_8)));
                    v.m0.forEach((x, y) -> {
                        ba.writeBytes(x.getBytes(UTF_8));
                        ba.writeBytes(y.getBytes(UTF_8));
                    });
                    ba.writeBytes(v.bi0.toByteArray());
                }
        );
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(ba.toByteArray());
        return Response.ok().entity(String.format("%064x%n", new BigInteger(1, digest.digest()))).build();
    }

    public static final String[] lorem = new String[]{
            "Lorem", "ipsum", "dolor", "sit", "amet,", "consectetur", "adipiscing", "elit.", "Nulla", "lacinia", "mollis",
            "rutrum.", "Orci", "varius", "natoque", "penatibus", "et", "magnis", "dis", "parturient", "montes,", "nascetur",
            "ridiculus", "mus.", "Cras", "sit", "amet", "nunc", "felis.", "Maecenas", "ut", "venenatis", "augue,", "nec",
            "viverra", "augue.", "Suspendisse", "ligula", "mi,", "cursus", "ac", "risus", "tristique,", "dapibus", "ultricies", "felis.", "In", "lorem",
            "metus,", "pulvinar", "ut", "est", "ac,", "semper", "tempor", "lorem.", "Donec", "sed", "semper", "purus,", "facilisis", "mollis", "odio.",
            "Quisque", "viverra", "magna", "ac", "elit", "ultricies", "hendrerit.", "Nunc", "pellentesque", "elementum", "egestas.", "", "Duis",
            "pulvinar", "urna", "quis", "posuere", "tincidunt.", "Class", "aptent", "taciti", "sociosqu", "ad", "litora", "torquent", "per", "conubia",
            "nostra,", "per", "inceptos", "himenaeos.", "Aliquam", "pharetra", "lacus", "at", "facilisis", "euismod.", "Proin", "tempor", "posuere",
            "gravida.", "Sed", "tincidunt", "id", "nisi", "in", "pretium.", "Sed", "tempus", "felis", "in", "risus", "viverra", "porta.", "Etiam",
            "pulvinar", "elit", "et", "ex", "pellentesque", "auctor.", "Praesent", "ut", "mauris", "vel", "lacus", "dignissim", "fringilla.",
            "Vestibulum", "et", "dapibus", "nulla.", "Integer", "interdum", "imperdiet", "tellus,", "quis", "dictum", "diam", "elementum", "eu.",
            "Maecenas", "laoreet", "eget", "diam", "sed", "gravida.", "Aenean", "sapien", "ipsum,", "placerat", "a", "sem", "in,", "aliquam",
            "eleifend", "risus.", "Praesent", "eget", "nulla", "ante.", "Ut", "tincidunt", "rhoncus", "molestie.", "", "In", "hac", "habitasse",
            "platea", "dictumst.", "Etiam", "suscipit,", "arcu", "sed", "auctor", "auctor,", "ligula", "enim", "tincidunt", "felis,", "in",
            "pellentesque", "lorem", "justo", "a", "risus.", "Interdum", "et", "malesuada", "fames", "ac", "ante", "ipsum", "primis", "in", "faucibus.",
            "Sed", "eget", "arcu", "eget", "risus", "rutrum", "accumsan", "a", "at", "arcu.", "Praesent", "vitae", "convallis", "felis.", "Nunc",
            "volutpat", "vel", "augue", "lobortis", "gravida.", "Nullam", "nec", "ullamcorper", "neque.", "Sed", "ac", "libero", "vitae", "leo",
            "fringilla", "aliquet.", "Vestibulum", "ante", "ipsum", "primis", "in", "faucibus", "orci", "luctus", "et", "ultrices", "posuere",
            "cubilia", "curae;", "Pellentesque", "facilisis", "mattis", "cursus.", "", "Cras", "imperdiet", "justo", "congue,", "porttitor", "dolor",
            "vel,", "finibus", "leo.", "Proin", "ligula", "ante,", "pretium", "eu", "pretium", "in,", "egestas", "sed", "justo.", "Donec", "interdum",
            "posuere", "est", "non", "sodales.", "Etiam", "lacus", "justo,", "laoreet", "vitae", "nulla", "eget,", "dignissim", "porta", "ex.", "In",
            "placerat", "sodales", "tellus,", "luctus", "tincidunt", "magna", "lobortis", "nec.", "Etiam", "commodo", "purus", "velit,", "ac",
            "accumsan", "mi", "semper", "a.", "Mauris", "nec", "sagittis", "lacus.", "Aliquam", "feugiat", "ipsum", "a", "nulla", "lobortis",
            "imperdiet.", "", "Integer", "tempus", "gravida", "lorem,", "in", "aliquet", "odio", "sagittis", "ac.", "Nam", "luctus", "sagittis",
            "turpis,", "non", "vehicula", "eros", "tincidunt", "et.", "Duis", "posuere", "lacus", "erat.", "Etiam", "viverra", "commodo", "luctus.",
            "Donec", "quis", "nulla", "id", "sem", "condimentum", "convallis.", "Maecenas", "vel", "auctor", "orci,", "vel", "ultrices", "erat.",
            "Suspendisse", "luctus", "porttitor", "fermentum.", "Sed", "in", "ipsum", "eget", "leo", "pretium", "maximus.", "Proin", "accumsan",
            "tellus", "a", "libero", "laoreet", "mollis", "venenatis", "sit", "amet", "metus.", "Donec", "nec", "convallis", "erat,", "nec", "iaculis",
            "sapien.", "", "Duis", "vestibulum", "elit", "fermentum", "leo", "consectetur,", "in", "tristique", "odio", "scelerisque.", "Etiam",
            "vestibulum", "est", "massa,", "ac", "placerat", "dolor", "interdum", "sollicitudin.", "Aenean", "pretium", "lorem", "at", "nulla",
            "hendrerit,", "eget", "fringilla", "enim", "rhoncus.", "Etiam", "sit", "amet", "tincidunt", "sapien.", "Vivamus", "at", "rhoncus", "risus.",
            "Aliquam", "quis", "mi", "quis", "tellus", "ultrices", "tempus", "vel", "vel", "dolor.", "In", "at", "arcu", "nec", "sapien", "aliquet",
            "rutrum", "sit", "amet", "ut", "leo.", "Curabitur", "semper", "imperdiet", "lectus,", "a", "auctor", "neque", "scelerisque", "at.", "",
            "Suspendisse", "facilisis", "feugiat", "lacus,", "non", "pretium", "risus", "fermentum", "quis.", "Interdum", "et", "malesuada", "fames",
            "ac", "ante", "ipsum", "primis", "in", "faucibus.", "Vestibulum", "ante", "ipsum", "primis", "in", "faucibus", "orci", "luctus", "et",
            "ultrices", "posuere", "cubilia", "curae;", "Donec", "suscipit", "augue", "finibus", "massa", "fringilla,", "at", "dapibus", "velit",
            "ullamcorper.", "Mauris", "tincidunt,", "orci", "non", "dictum", "hendrerit,", "leo", "neque", "accumsan", "massa,", "quis", "tempor",
            "ligula", "arcu", "at", "sem.", "Donec", "volutpat", "ipsum", "in", "efficitur", "ullamcorper.", "Suspendisse", "potenti.", "Donec",
            "tristique", "et", "purus", "eget", "luctus.", "Pellentesque", "faucibus", "sem", "ac", "nibh", "mollis", "malesuada", "tempor", "quis",
            "sapien.", "Cras", "ac", "faucibus", "dolor,", "vitae", "ultricies", "odio.", "Nunc", "fermentum", "maximus", "dolor,", "at", "auctor",
            "nunc", "mattis", "vitae.", "Integer", "nec", "tellus", "egestas,", "aliquam", "sem", "id,", "venenatis", "lacus.", "Etiam", "et", "elit",
            "nec", "justo", "gravida", "condimentum.", "Phasellus", "luctus", "consectetur", "ligula,", "sed", "tempus", "nulla", "rhoncus", "ac.",
            "Ut", "non", "dapibus", "ante.", "Nullam", "feugiat", "dignissim", "leo", "id", "cursus.", "", "Class", "aptent", "taciti", "sociosqu",
            "ad", "litora", "torquent", "per", "conubia", "nostra,", "per", "inceptos", "himenaeos.", "Aliquam", "vel", "augue", "eu", "lacus",
            "faucibus", "hendrerit", "vel", "ac", "nisl.", "Nunc", "imperdiet", "lectus", "quam,", "quis", "ullamcorper", "erat", "eleifend", "nec.",
            "Vivamus", "ut", "tellus", "feugiat", "purus", "suscipit", "pellentesque", "id", "sit", "amet", "diam.", "Nam", "lacinia", "metus", "ex,",
            "ac", "dictum", "sapien", "consectetur", "quis.", "Phasellus", "euismod", "augue", "libero,", "et", "rutrum", "nisi", "interdum", "ut.",
            "Suspendisse", "quis", "nisi", "vel", "nibh", "ultrices", "porttitor", "pharetra", "porta", "velit.", "", "Morbi", "eu", "libero", "ex.",
            "Vestibulum", "sapien", "odio,", "posuere", "non", "dapibus", "eleifend,", "euismod", "eget", "enim.", "Donec", "ultrices", "ex", "at",
            "nibh", "condimentum,", "vitae", "sollicitudin", "dui", "blandit.", "Duis", "mollis", "nisl", "nec", "magna", "efficitur", "consectetur",
            "id", "et", "lacus.", "Cras", "pharetra", "arcu", "vitae", "dolor", "porttitor", "interdum", "non", "et", "tellus.", "Morbi", "aliquet",
            "justo", "sed", "libero", "convallis", "ullamcorper.", "Suspendisse", "pharetra", "urna", "ac", "magna", "pulvinar,", "ac", "vehicula",
            "ipsum", "vestibulum.", "In", "pretium", "posuere", "tortor", "sed", "mollis.", "Suspendisse", "vel", "quam", "dictum,", "pulvinar",
            "tellus", "eget,", "sodales", "augue.", "Aliquam", "ultricies,", "sapien", "sit", "amet", "imperdiet", "ultrices,", "ante", "est",
            "suscipit", "lectus,", "ac", "euismod", "leo", "nunc", "at", "diam.", "", "Duis", "id", "arcu", "tempor,", "fringilla", "massa", "eu,",
            "feugiat", "massa.", "Ut", "non", "risus", "viverra,", "interdum", "nisl", "viverra,", "luctus", "dolor.", "Aliquam", "at", "nisl", "nisi.",
            "Sed", "luctus", "purus", "vel", "lacus", "elementum", "eleifend.", "Vestibulum", "fringilla", "leo", "vitae", "sapien", "malesuada",
            "sollicitudin.", "Suspendisse", "fermentum", "tincidunt", "tincidunt.", "Vivamus", "et", "consequat", "erat.", "Donec", "a", "malesuada",
            "ante.", "Proin", "tristique", "libero", "a", "vestibulum", "ultricies.", "Nullam", "tincidunt", "quam", "placerat,", "rhoncus", "ex",
            "ac,", "eleifend", "nulla.", "Pellentesque", "non", "sapien", "vitae", "ligula", "rutrum", "commodo", "non", "vehicula", "nulla.",
            "Pellentesque", "viverra", "elit", "in", "velit", "scelerisque,", "at", "cursus", "nisl", "laoreet.", "Mauris", "pretium", "metus", "et",
            "porttitor", "pharetra.", "Curabitur", "lacus", "dolor,", "aliquet", "sit", "amet", "eleifend", "nec,", "consequat", "a", "ante.", "",
            "Nunc", "tristique", "risus", "a", "urna", "maximus", "pellentesque.", "Ut", "feugiat", "felis", "diam,", "eget", "dapibus", "arcu",
            "vestibulum", "vitae.", "Nullam", "in", "dui", "at", "metus", "facilisis", "aliquet.", "Quisque", "a", "sagittis", "ante.", "Fusce",
            "vestibulum", "nunc", "odio,", "nec", "iaculis", "dui", "feugiat", "in.", "Integer", "fermentum", "auctor", "malesuada.", "Cras",
            "faucibus", "accumsan", "viverra.", "Sed", "gravida", "sapien", "quis", "risus", "hendrerit", "sollicitudin.", "Praesent", "ac",
            "elementum", "quam.", "Fusce", "tempus", "quis", "mauris", "et", "dignissim.", "Interdum", "et", "malesuada", "fames", "ac", "ante",
            "ipsum", "primis", "in", "faucibus.", "Pellentesque", "massa", "nisl,", "varius", "in", "porttitor", "eget,", "gravida", "quis", "nulla.",
            "Nulla", "tempor", "arcu", "orci,", "in", "eleifend", "lorem", "venenatis", "eget.", "", "Praesent", "pretium", "nisi", "quis", "diam",
            "luctus,", "eget", "pulvinar", "enim", "lacinia.", "Nunc", "eros", "enim,", "dignissim", "nec", "hendrerit", "non,", "egestas", "non",
            "ipsum.", "Sed", "pellentesque", "interdum", "sem.", "Vestibulum", "fringilla", "quam", "bibendum", "bibendum", "pulvinar.", "Integer",
            "purus", "velit,", "consectetur", "ut", "dolor", "quis,", "venenatis", "vehicula", "augue.", "Cras", "lacinia", "sagittis", "risus", "in",
            "venenatis.", "Cras", "egestas", "odio", "at", "accumsan", "porta.", "Etiam", "cursus", "sem", "nec", "augue", "euismod,", "nec", "tempus",
            "dolor", "mollis.", "Vestibulum", "commodo", "viverra", "vehicula.", "Class", "aptent", "taciti", "sociosqu", "ad", "litora", "torquent",
            "per", "conubia", "nostra,", "per", "inceptos", "himenaeos.", "Praesent", "quis", "erat", "quam.", "Aliquam", "ut", "tortor", "at", "diam",
            "congue", "eleifend.", "Cras", "vestibulum", "dignissim", "pellentesque.", "Sed", "vel", "nisl", "ex.", "", "Cras", "a", "malesuada",
            "enim.", "Nam", "mollis", "pretium", "tempor.", "Quisque", "dapibus", "vehicula", "risus.", "Curabitur", "lectus", "ligula,", "aliquam",
            "id", "urna", "sit", "amet,", "finibus", "dictum", "massa.", "Quisque", "porta", "a", "purus", "in", "eleifend.", "Aliquam", "tempor,",
            "urna", "vitae", "ultricies", "ornare,", "tortor", "nisl", "consequat", "eros,", "at", "tempor", "est", "tortor", "sed", "tortor.",
            "Maecenas", "tempor", "nunc", "ornare", "nibh", "malesuada,", "sed", "facilisis", "diam", "imperdiet.", "Integer", "a", "vehicula",
            "massa.", "Quisque", "sit", "amet", "justo", "tristique,", "gravida", "enim", "sit", "amet,", "tempor", "leo.", "Sed", "convallis",
            "lacus", "vel", "accumsan", "congue.", "Suspendisse", "scelerisque", "orci", "at", "leo", "rhoncus", "eleifend.", "", "Maecenas", "eget",
            "imperdiet", "nisl,", "ut", "aliquam", "leo.", "Suspendisse", "congue", "pretium", "dolor", "eget", "semper.", "Donec", "mauris", "risus,",
            "ultrices", "ultricies", "turpis", "ullamcorper,", "finibus", "scelerisque", "nulla.", "Aliquam", "sed", "elit", "imperdiet,", "posuere",
            "dui", "quis,", "facilisis", "felis.", "Interdum", "et", "malesuada", "fames", "ac", "ante", "ipsum", "primis", "in", "faucibus.", "Ut",
            "mollis", "non", "nunc", "nec", "elementum.", "Suspendisse", "at", "urna", "auctor,", "viverra", "purus", "vulputate,", "fermentum",
            "lorem.", "Ut", "libero", "mauris,", "vestibulum", "at", "ultricies", "vel,", "eleifend", "ut", "sapien.", "Donec", "vel", "cursus",
            "eros.", "Quisque", "sodales", "est", "nisl,", "id", "eleifend", "metus", "molestie", "at.", "In", "pretium", "ullamcorper", "libero",
            "sit", "amet", "gravida.", "Fusce", "hendrerit", "felis", "vitae", "augue", "laoreet", "porta.", "Morbi", "et", "bibendum", "nulla.", "",
            "Aliquam", "rhoncus", "libero", "facilisis", "iaculis", "mollis.", "Proin", "et", "efficitur", "purus.", "Vestibulum", "pharetra",
            "eleifend", "leo,", "eu", "mattis", "ipsum", "gravida", "nec.", "Suspendisse", "potenti.", "Donec", "nec", "mauris", "in", "purus",
            "condimentum", "aliquet", "at", "sit", "amet", "nulla.", "Nullam", "faucibus", "nec", "augue", "ac", "imperdiet.", "Morbi", "vel",
            "malesuada", "purus,", "eget", "molestie", "tortor.", "Sed", "et", "semper", "magna.", "Phasellus", "finibus", "mi", "non", "mi",
            "facilisis,", "ac", "mattis", "tortor", "molestie.", "Nunc", "porta", "a", "erat", "eu", "suscipit.", "Integer", "consectetur", "neque",
            "eget", "nulla", "feugiat", "scelerisque.", "Phasellus", "molestie", "dui", "nulla,", "suscipit", "commodo", "ligula", "consectetur",
            "sed.", "Nullam", "a", "elit", "tempus,", "porta", "eros", "rhoncus,", "ullamcorper", "quam.", "Nam", "scelerisque", "ut", "urna", "sit",
            "amet", "laoreet.", "Suspendisse", "vitae", "auctor", "sapien.", "", "Ut", "maximus", "orci", "tempus", "quam", "vestibulum,", "vel",
            "gravida", "dui", "malesuada.", "Proin", "vulputate", "tortor", "ac", "laoreet", "auctor.", "Donec", "id", "tortor", "turpis.", "Quisque",
            "sit", "amet", "purus", "a", "ipsum", "laoreet", "maximus.", "Donec", "nisi", "dui,", "pharetra", "imperdiet", "sodales", "in,",
            "imperdiet", "id", "velit.", "Mauris", "convallis", "malesuada", "varius.", "Praesent", "vel", "purus", "interdum,", "congue", "est",
            "nec,", "cursus", "justo.", "Aenean", "sapien", "metus,", "porta", "non", "condimentum", "a,", "commodo", "in", "erat.", "Mauris", "vel",
            "massa", "id", "enim", "eleifend", "mattis.", "Nunc", "malesuada", "fermentum", "diam", "in", "eleifend.", "Morbi", "sagittis",
            "vulputate", "nibh,", "sed", "tempus", "nulla", "dapibus", "ac.", "Sed", "neque", "massa,", "congue", "eu", "molestie", "in,", "porta",
            "vel", "nisl.", "", "Ut", "orci", "metus,", "faucibus", "ut", "pharetra", "ut,", "venenatis", "et", "mauris.", "Phasellus", "tempus",
            "est", "a", "elit", "consectetur", "tristique.", "Aliquam", "tincidunt", "ullamcorper", "lacus", "sit", "amet", "vestibulum.", "Vivamus",
            "vel", "tellus", "vel", "risus", "mattis", "feugiat.", "Proin", "at", "gravida", "ipsum.", "Etiam", "semper", "facilisis", "convallis.",
            "Aliquam", "blandit", "feugiat", "suscipit.", "Aenean", "ac", "tincidunt", "justo,", "sed", "semper", "turpis.", "Cras", "ac", "enim",
            "nisi.", "In", "euismod", "augue", "vulputate,", "porta", "lacus", "a", "orci", "aliquam."
    };

}
