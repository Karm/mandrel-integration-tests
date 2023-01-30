FROM registry.access.redhat.com/ubi8/ubi-minimal:8.7
RUN microdnf install freetype fontconfig
WORKDIR /work/
RUN chown 100 /work \
    && chmod "g+rwX" /work \
    && chown 1000:root /work
