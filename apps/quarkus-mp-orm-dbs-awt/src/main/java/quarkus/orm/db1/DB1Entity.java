package quarkus.orm.db1;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "db1entity")
public class DB1Entity extends PanacheEntity {
    public String field;
}
