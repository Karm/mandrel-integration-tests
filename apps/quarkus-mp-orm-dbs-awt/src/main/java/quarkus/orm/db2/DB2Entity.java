package quarkus.orm.db2;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "db2entity")
public class DB2Entity extends PanacheEntity {
    public String field;
}
