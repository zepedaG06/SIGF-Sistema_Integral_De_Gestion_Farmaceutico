package org.sigf.repositories;
import java.sql.*;
public final class StockMovementRepository {private StockMovementRepository(){} public static void write(Connection c,long batch,int qty,String kind,Long ref,long actor)throws SQLException{try(PreparedStatement s=c.prepareStatement("insert into stock_movement(batch_id,quantity,kind,reference_id,actor_id) values(?,?,?,?,?)")){s.setLong(1,batch);s.setInt(2,qty);s.setString(3,kind);if(ref==null)s.setNull(4,Types.BIGINT);else s.setLong(4,ref);s.setLong(5,actor);s.executeUpdate();}}}
