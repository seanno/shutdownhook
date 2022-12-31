''
'' Read about this code at http://shutdownhook.com
'' MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
''

sub Main(args as Dynamic)

  ' check input args
  if args.mediaType <> invalid and args.contentId <> invalid
	handleArgs(args.mediaType, args.contentId)
  end if
  
  ' set up screen and port
  print "in showChannelSGScreen"
  screen = CreateObject("roSGScreen")
  m.port = CreateObject("roMessagePort")
  screen.setMessagePort(m.port)

  ' load and show scene
  scene = screen.CreateScene("Ferries")
  screen.show()
  scene.signalBeacon("AppLaunchComplete")

  ' message loop
  while(true)
	
    msg = wait(0, m.port)
    msgType = type(msg)
	
    if msgType = "roSGScreenEvent"
      if msg.isScreenClosed() then return
    end if

	if msgType = "roInputEvent"
	  if msg.IsInput()

		info = msg.GetInfo()
		if info.DoesExist("mediatype") and info.DoesExist("contentid")
		  handleArgs(info.mediatype, info.contentid)
		end if
		
	  end if
	end if
	
  end while
  
end sub

function handleArgs(mediaType as String, contentId as String)
  ' nothing for us to do but we need to handle the event
  ' to pass certification and maybe we'll use it someday,
  ' like to accept an inbound terminal name
  ? "DEEPLINK => MediaType: ";
  ? mediaType;
  ? "; ContentId: ";
  ? contentId
end function


