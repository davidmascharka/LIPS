package com.davidmascharka.lips;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.mascharka.indoorlocalization.R;

/**
 *  Copyright 2015 David Mascharka
 * 
 * This file is part of LIPS (Learning-based Indoor Positioning System).
 *
 *  LIPS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  LIPS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with LIPS.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * @author David Mascharka (david.mascharka@drake.edu)
 * 
 * Allows the user to specify a room/building size
 * 
 * Really nothing in here to customize. Simple dialog to get two
 * numbers for x and y. A third coordinate could be used to specify
 * how many floors if the application uses that. Would edit
 * dialog_select_room_size.xml to add a third EditText, then add a
 * third option to the onClick inside onCreateDialog here
 *
 */
public class SelectRoomSizeDialogFragment extends DialogFragment {
	/* The activity that creates an instance of this dialog fragment must
         * implement this interface in order to receive event callbacks.
         * The method passes the room size the user entered
         */
	public interface SelectRoomSizeDialogListener {
		public void onRoomSizeChanged(int width, int length);
	}
	
	// Use this instance of the interface to deliver action events
	SelectRoomSizeDialogListener listener;
	
	// Instantiate the SelectRoomSizeDialogListener
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		// Verify the host activity implements the callback interface
		try {
			// Instantiate the listener so we can send events to the host
			listener = (SelectRoomSizeDialogListener) activity;
		} catch (ClassCastException e) {
			// The activity doesn't implement the interface
			throw new ClassCastException(activity.toString() +
					"must implement SelectRoomSizeDialogListener");
		}
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		
		LayoutInflater inflater = getActivity().getLayoutInflater();
		
		final View view = inflater.inflate(R.layout.dialog_select_room_size, null);
		
		builder.setTitle(R.string.dialog_select_room_size)
			.setView(view)
			.setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					EditText width = (EditText) view.findViewById(R.id.room_width);
					EditText length = (EditText) view.findViewById(R.id.room_length);
					
					listener.onRoomSizeChanged(
							Integer.parseInt(
									width.getText().toString()), 
							Integer.parseInt(length.getText().toString()));
				}
			})
			.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					// Don't do anything - changes not saved
				}
			});
		
		return builder.create();
	}
}