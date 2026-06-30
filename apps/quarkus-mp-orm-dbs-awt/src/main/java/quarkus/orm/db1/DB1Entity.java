package quarkus.orm.db1;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "db1entity")
public class DB1Entity extends PanacheEntity {
    public String field;
}
