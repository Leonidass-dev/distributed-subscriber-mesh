# encoding: utf-8
require 'socket'
require_relative 'lib/hasup/capacity_pb'
require_relative 'lib/hasup/configuration_pb'

class AdminClient
  SERVERS = {
    1 => { host: 'localhost', port: 7001 },
    2 => { host: 'localhost', port: 7002 },
    3 => { host: 'localhost', port: 7003 }
  }

  def initialize
    @sockets = {}
  end

  # Sunucuya bağlan
  def connect_to_server(server_id)
    server = SERVERS[server_id]
    return nil unless server

    begin
      socket = TCPSocket.new(server[:host], server[:port])
      @sockets[server_id] = socket
      puts "Server #{server_id} baglandi".encode('utf-8')
      true
    rescue Errno::ECONNREFUSED => e
      puts "Server #{server_id} baglanamadi: Sunucu calismiyordu".encode('utf-8')
      false
    rescue => e
      puts "Server #{server_id} baglanamadi: #{e.message}".encode('utf-8')
      false
    end
  end

  # Sunucunun kapasitesini sorgula
  def query_capacity(server_id)
    socket = @sockets[server_id]
    return nil unless socket

    begin
      # Kapasite sorgusu gönder
      request = Hasup::Capacity.new(
        server_id: server_id,
        server_status: 0,
        timestamp: Time.now.to_i
      )

      # İlk byte olarak mesaj tipini gönder (1 = Capacity request)
      socket.write([1].pack('C'))
      
      # Protobuf mesajını gönder
      data = request.to_proto
      socket.write([data.bytesize].pack('N'))
      socket.write(data)

      # Yanıtı oku
      response_size = socket.read(4).unpack('N')[0]
      response_data = socket.read(response_size)
      response = Hasup::Capacity.decode(response_data)

      puts "Sunucu #{server_id} kapasitesi:"
      puts "  Abone sayısı: #{response.server_status}"
      puts "  Zaman: #{Time.at(response.timestamp)}"
      
      response
    rescue => e
      puts "Kapasite sorgusu başarısız: #{e.message}"
      nil
    end
  end

  # Bağlantıları kapat
  def close
    @sockets.each_value(&:close)
    @sockets.clear
  end
end

# Test
if __FILE__ == $0
  admin = AdminClient.new

  # Her sunucuya tek tek bağlanmayı dene
  (1..3).each do |server_id|
    puts "\nServer #{server_id} baglanti denemesi:".encode('utf-8')
    if admin.connect_to_server(server_id)
      puts "Kapasite sorgusu yapiliyor...".encode('utf-8')
      admin.query_capacity(server_id)
    end
    sleep(1)  # Sunucular arasında biraz bekle
  end

  admin.close
end
