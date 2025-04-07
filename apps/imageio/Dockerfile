FROM registry.access.redhat.com/ubi9/ubi-minimal:9.5
RUN microdnf install freetype fontconfig -y
WORKDIR /work/
RUN chown 100 /work \
    && chmod "g+rwX" /work \
    && chown 1000:root /work
