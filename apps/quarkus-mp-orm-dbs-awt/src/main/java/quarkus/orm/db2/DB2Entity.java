package quarkus.orm.db2;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "db2entity")
public class DB2Entity extends PanacheEntity {
    public String field;
}
