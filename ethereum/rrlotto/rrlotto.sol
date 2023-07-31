
// Read about this code at http://shutdownhook.com
// MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
// SPDX-License-Identifier: MIT

pragma solidity ^0.8.10;

contract RoundRobinLotto
{
	address house;
	uint countdown;

	uint constant MAX_CYCLE = 25;
	uint constant WEI_TO_PLAY = 0.001 ether;
	uint constant HOUSE_PERCENTAGE = 5;

	constructor() {
		house = msg.sender;
		resetCountdown();
	}

	// fired on payout
	event Payout(address indexed to, uint amount);

	// public interface
	function play() public payable {

		require(msg.value == WEI_TO_PLAY);
		
		if (--countdown > 0) {
			return;
		}

		// do this here, before the transfers. If something explodes
		// during the transfers, the kitty will just grow a little extra.
		resetCountdown();

		// payout!
		payable(house).transfer(address(this).balance * HOUSE_PERCENTAGE / 100);

		uint payout = address(this).balance;
		payable(msg.sender).transfer(payout);
		emit Payout(msg.sender, payout);
	}

	// This lets us "clean up" by destroying the contract. Of course it never
	// leaves the chain, but all of its state/data is removed. This benefits
	// the chain overall so calling this actually costs negative gas.
	function destroy() houseOnly public {
		selfdestruct(payable(house));
	}
	
	// The below defines a "modifier" that can be applied to function signatures.
	// Functions with the modifier are "wrapped" with the code below; the actual
	// function code is dropped in place of the _ marker
	
	modifier houseOnly {
		require(msg.sender == house);
		_;
	}

	// Smart Contracts cannot be random by definition --- everybody who runs the
	// code must come to the same conclusion. The code below does a nice job of
	// generating pseudo-randomness between 2 and MAX_CYCLE for our purposes.

	function resetCountdown() private {
		countdown = (uint(keccak256(abi.encodePacked(block.difficulty, block.timestamp)))
					 % (MAX_CYCLE - 1)) + 2;
	}
		
	// No payable fallback function means we can't accept vanilla value
	// transfers into the contract account. We could enable "gifting" value 
	// into the kitty by uncommenting the line below, but I think that just
	// makes us a great money laundering service. ;)
	// 
	// function () public payable { }
}
