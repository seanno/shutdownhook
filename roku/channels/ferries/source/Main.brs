''
'' Read about this code at http://shutdownhook.com
'' MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
''
sub Main()

    ' set up screen and port
	print "in showChannelSGScreen"
    screen = CreateObject("roSGScreen")
    m.port = CreateObject("roMessagePort")
    screen.setMessagePort(m.port)

	' load and show scene
    scene = screen.CreateScene("Ferries")
    screen.show()

	' message loop
    while(true)
        msg = wait(0, m.port)
        msgType = type(msg)
        if msgType = "roSGScreenEvent"
          if msg.isScreenClosed() then return
        end if
      end while
	  
end sub

